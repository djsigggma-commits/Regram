package app.regram.pillstack.pills.weather;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ClipRoundedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LocationActivity;

import java.util.Locale;

import app.regram.pillstack.PillStackConfig;
import app.regram.pillstack.PillStackEvents;
import app.regram.pillstack.PillStackSettingsActivity;
import app.regram.pillstack.PillType;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Экран настроек погодной пилюли (порт
 * {@code com.exteragram.messenger.pillstack.ui.pills.weather.WeatherSettingsActivity}).
 *
 * Выбор источника координат: текущая геопозиция либо точка на карте. Во втором случае
 * показывается статичное превью карты (тот же WebFile-рендер, что и в стоковом Telegram)
 * и подпись адреса; тап по превью открывает {@link LocationActivity}.
 *
 * Экран exteraGram построен на {@code BasePreferencesActivity}/{@code UItem}; у нас общий предок
 * настроек — {@link BaseNekoSettingsActivity}, поэтому кастомные строки (превью и адрес)
 * вынесены в собственные типы вьюхолдеров (>= 100).
 */
public class WeatherSettingsActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_MAP_PREVIEW = 100;
    private static final int TYPE_ADDRESS = 101;

    private static final int MAP_HEIGHT_DP = 240;

    private int pillsRow;
    private int pillsDividerRow;

    private int locationHeaderRow;
    private int currentLocationRow;
    private int customLocationRow;
    private int mapPreviewRow;
    private int addressRow;
    private int locationInfoRow;

    private int permissionRow;
    private int permissionInfoRow;

    private FrameLayout mapPreviewContainer;
    private BackupImageView mapPreview;
    private View mapMarker;
    private ClipRoundedDrawable mapLoadingDrawable;

    private FrameLayout addressContainer;
    private TextView addressText;

    public WeatherSettingsActivity() {
        super();
        PillStackConfig.init();
    }

    // ---- Состояние геолокации ----

    /** Выдано ли хотя бы одно из разрешений на геопозицию. */
    public static boolean isLocationPermissionGranted() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return false;
        }
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Включены ли службы геолокации в системе. */
    public static boolean isLocationEnabled() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return false;
            }
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return false;
            }
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.PillStackWeather);
    }

    @Override
    protected String getKey() {
        return "pillstackweather";
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        pillsRow = addRow("pills");
        pillsDividerRow = addRow();

        locationHeaderRow = addRow("weatherLocation");
        currentLocationRow = addRow("weatherCurrentLocation");
        customLocationRow = addRow("weatherCustomLocation");

        boolean current = PillStackConfig.useCurrentLocation();
        if (current) {
            mapPreviewRow = -1;
            addressRow = -1;
        } else {
            mapPreviewRow = addRow();
            addressRow = TextUtils.isEmpty(PillStackConfig.customWeatherAddress()) ? -1 : addRow();
        }
        locationInfoRow = addRow();

        if (current && !isLocationPermissionGranted()) {
            permissionRow = addRow("weatherPermission");
            permissionInfoRow = addRow();
        } else if (current && !isLocationEnabled()) {
            permissionRow = addRow("weatherLocationServices");
            permissionInfoRow = addRow();
        } else {
            permissionRow = -1;
            permissionInfoRow = -1;
        }
    }

    @Override
    public View createView(Context context) {
        addressText = new TextView(context);
        addressText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        addressText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        addressText.setGravity(Gravity.CENTER);

        addressContainer = new FrameLayout(context);
        addressContainer.addView(addressText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.TOP, 21, 15, 21, 15));
        addressContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        mapPreview = new BackupImageView(context) {
            @Override
            public ImageReceiver createImageReciever() {
                return new ImageReceiver(this) {
                    @Override
                    public boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                        if (drawable != null && type != ImageReceiver.TYPE_THUMB && mapMarker != null) {
                            mapMarker.animate().alpha(1f).translationY(0)
                                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_BACK)
                                    .setDuration(250).start();
                        }
                        return super.setImageBitmapByKey(drawable, key, type, memCache, guid);
                    }
                };
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(MAP_HEIGHT_DP), MeasureSpec.EXACTLY));
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == mapLoadingDrawable || super.verifyDrawable(who);
            }
        };
        mapPreview.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        try {
            SvgHelper.SvgDrawable placeholder = DocumentObject.getSvgThumb(R.raw.map_placeholder, Theme.key_chat_outLocationIcon, 0.2f);
            if (placeholder != null) {
                placeholder.setColorKey(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
                placeholder.setAspectCenter(true);
                placeholder.setParent(mapPreview.getImageReceiver());
                mapLoadingDrawable = new ClipRoundedDrawable(placeholder);
                mapLoadingDrawable.setCallback(mapPreview);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        mapMarker = new View(context) {
            private final Drawable pin = getContext().getResources().getDrawable(R.drawable.map_pin_photo).mutate();
            private final AvatarDrawable avatarDrawable = new AvatarDrawable();
            private final ImageReceiver avatarImage = new ImageReceiver(this);

            {
                avatarDrawable.setInfo(getUserConfig().getCurrentUser());
                avatarImage.setForUserOrChat(getUserConfig().getCurrentUser(), avatarDrawable);
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                pin.setBounds(0, 0, AndroidUtilities.dp(62), AndroidUtilities.dp(85));
                pin.draw(canvas);
                avatarImage.setRoundRadius(AndroidUtilities.dp(62));
                avatarImage.setImageCoords(AndroidUtilities.dp(6), AndroidUtilities.dp(6),
                        AndroidUtilities.dp(50), AndroidUtilities.dp(50));
                avatarImage.draw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(62), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85), MeasureSpec.EXACTLY));
            }
        };

        mapPreviewContainer = new FrameLayout(context);
        mapPreviewContainer.addView(mapPreview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, MAP_HEIGHT_DP));
        mapPreviewContainer.addView(mapMarker, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, -31, 0, 0));
        mapPreviewContainer.setOnClickListener(v -> openMapPicker());

        View view = super.createView(context);
        updateMapPreview();
        return view;
    }

    private void updateMapPreview() {
        if (mapPreview == null || mapMarker == null) {
            return;
        }
        if (!PillStackConfig.hasCustomWeatherLocation()) {
            mapPreview.setImageBitmap((android.graphics.Bitmap) null);
            return;
        }
        mapMarker.setAlpha(0f);
        mapMarker.setTranslationY(-AndroidUtilities.dp(12));

        int measured = mapPreview.getMeasuredWidth() <= 0 ? AndroidUtilities.displaySize.x : mapPreview.getMeasuredWidth();
        int width = (int) (measured / AndroidUtilities.density);
        int scale = Math.min(2, (int) Math.ceil(AndroidUtilities.density));
        try {
            WebFile file = WebFile.createWithGeoPoint(
                    PillStackConfig.customWeatherLatitude(), PillStackConfig.customWeatherLongitude(),
                    0L, width * scale, MAP_HEIGHT_DP * scale, 15, scale);
            mapPreview.setImage(ImageLocation.getForWebFile(file), width + "_" + MAP_HEIGHT_DP,
                    mapLoadingDrawable, 0, null);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (addressText != null) {
            addressText.setText(PillStackConfig.customWeatherAddress());
        }
    }

    private void updateRowsAndNotify() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void applyLocationMode(boolean useCurrent) {
        PillStackConfig.setUseCurrentLocation(useCurrent);
        PillStackEvents.notifySettingsChanged(PillType.WEATHER.id);
        updateMapPreview();
        updateRowsAndNotify();
    }

    private void openMapPicker() {
        LocationActivity fragment = new LocationActivity(LocationActivity.LOCATION_TYPE_SEND);
        if (PillStackConfig.hasCustomWeatherLocation()) {
            try {
                TLRPC.TL_channelLocation initial = new TLRPC.TL_channelLocation();
                TLRPC.TL_geoPoint point = new TLRPC.TL_geoPoint();
                point.lat = PillStackConfig.customWeatherLatitude();
                point._long = PillStackConfig.customWeatherLongitude();
                initial.geo_point = point;
                initial.address = PillStackConfig.customWeatherAddress();
                fragment.setInitialLocation(initial);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        fragment.setDelegate((location, live, notify, scheduleDate, payStars) -> {
            if (location == null || location.geo == null) {
                return;
            }
            String address = location instanceof TLRPC.TL_messageMediaVenue
                    ? ((TLRPC.TL_messageMediaVenue) location).address
                    : null;
            PillStackConfig.setCustomWeatherLocation(location.geo.lat, location.geo._long, address);
            PillStackConfig.setUseCurrentLocation(false);
            PillStackEvents.notifySettingsChanged(PillType.WEATHER.id);
            AndroidUtilities.runOnUIThread(() -> {
                updateMapPreview();
                updateRowsAndNotify();
            });
        });
        presentFragment(fragment);
    }

    private void openSystemLocationSettings() {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        try {
            context.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void requestLocationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && getParentActivity() != null) {
                getParentActivity().requestPermissions(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, 2);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // разрешение/службы могли поменяться в системных настройках — пересобираем список
        updateRowsAndNotify();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == pillsRow) {
            presentFragment(new PillStackSettingsActivity());
        } else if (position == currentLocationRow) {
            if (!PillStackConfig.useCurrentLocation()) {
                applyLocationMode(true);
            }
            if (!isLocationPermissionGranted()) {
                requestLocationPermission();
            }
        } else if (position == customLocationRow) {
            if (PillStackConfig.useCurrentLocation()) {
                applyLocationMode(false);
            }
            openMapPicker();
        } else if (mapPreviewRow != -1 && (position == mapPreviewRow || position == addressRow)) {
            openMapPicker();
        } else if (permissionRow != -1 && position == permissionRow) {
            if (!isLocationPermissionGranted()) {
                requestLocationPermission();
            } else {
                openSystemLocationSettings();
            }
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == locationHeaderRow) {
                return TYPE_HEADER;
            }
            if (position == currentLocationRow || position == customLocationRow) {
                return TYPE_RADIO;
            }
            if (position == mapPreviewRow) {
                return TYPE_MAP_PREVIEW;
            }
            if (position == addressRow) {
                return TYPE_ADDRESS;
            }
            if (position == pillsDividerRow) {
                return TYPE_SHADOW;
            }
            if (position == locationInfoRow || position == permissionInfoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_MAP_PREVIEW || type == TYPE_ADDRESS) {
                return true;
            }
            return super.isEnabled(holder);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_MAP_PREVIEW || viewType == TYPE_ADDRESS) {
                FrameLayout wrapper = new FrameLayout(mContext);
                wrapper.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                wrapper.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new org.telegram.ui.Components.RecyclerListView.Holder(wrapper);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    ((HeaderCell) holder.itemView).setText(getString(R.string.PillStackWeatherLocationHeader));
                    break;
                }
                case TYPE_RADIO: {
                    TextRadioCell cell = (TextRadioCell) holder.itemView;
                    if (position == currentLocationRow) {
                        cell.setTextAndCheck(getString(R.string.PillStackWeatherCurrentLocation),
                                PillStackConfig.useCurrentLocation(), true);
                    } else {
                        cell.setTextAndValueAndCheck(getString(R.string.PillStackWeatherSelectLocation),
                                getLocationValue(), !PillStackConfig.useCurrentLocation(), false, false);
                    }
                    break;
                }
                case TYPE_MAP_PREVIEW: {
                    attach((FrameLayout) holder.itemView, mapPreviewContainer);
                    break;
                }
                case TYPE_ADDRESS: {
                    if (addressText != null) {
                        addressText.setText(PillStackConfig.customWeatherAddress());
                    }
                    attach((FrameLayout) holder.itemView, addressContainer);
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == locationInfoRow) {
                        cell.setText(getString(R.string.PillStackWeatherSettingsInfo));
                    } else {
                        cell.setText(isLocationPermissionGranted()
                                ? getString(R.string.GpsDisabledAlertText)
                                : getString(R.string.PillStackWeatherPermissionInfo));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == pillsRow) {
                        cell.setTextAndIcon(getString(R.string.PillStackActivePills), R.drawable.msg_settings_old, false);
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    } else if (position == permissionRow) {
                        if (!isLocationPermissionGranted()) {
                            cell.setTextAndIcon(getString(R.string.PillStackWeatherPermissionGrant), R.drawable.report, false);
                            cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                        } else {
                            cell.setTextAndIcon(getString(R.string.PillStackWeatherServicesEnable), R.drawable.filled_location, false);
                            cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                        }
                    }
                    break;
                }
            }
        }

        private void attach(FrameLayout wrapper, View content) {
            if (content == null) {
                return;
            }
            if (content.getParent() == wrapper) {
                return;
            }
            if (content.getParent() instanceof ViewGroup) {
                ((ViewGroup) content.getParent()).removeView(content);
            }
            wrapper.removeAllViews();
            wrapper.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    private String getLocationValue() {
        String address = PillStackConfig.customWeatherAddress();
        if (!TextUtils.isEmpty(address)) {
            return address;
        }
        if (PillStackConfig.hasCustomWeatherLocation()) {
            return String.format(Locale.US, "%.4f, %.4f",
                    PillStackConfig.customWeatherLatitude(), PillStackConfig.customWeatherLongitude());
        }
        return getString(R.string.PillStackWeatherLocationNotSet);
    }
}
