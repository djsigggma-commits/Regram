/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import app.regram.appearance.AppearanceConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import xyz.nextalone.nagram.NaConfig;

public class DividerCell extends View {

    private boolean forceDarkTheme;
    private Paint paint = new Paint();
    private Theme.ResourcesProvider resourcesProvider;

    public DividerCell(Context context) {
        this(context, null);
    }

    public DividerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), getPaddingTop() + getPaddingBottom() + 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Линия рисуется только в режиме «Линия» — как у exteraGram
        // (12.9.0:DividerCell.java:39). Ветка forceDarkTheme берёт цвет не из
        // key_divider, поэтому патча в Theme.getColor тут не хватает: в режиме
        // «Сегменты» линия иначе осталась бы поверх карточек.
        if (NaConfig.INSTANCE.getHideDividers().Bool() || AppearanceConfig.dividerHidden()) {
            return;
        }
        if (forceDarkTheme) {
            paint.setColor(ColorUtils.blendARGB(Color.BLACK, Theme.getColor(Theme.key_voipgroup_dialogBackground, resourcesProvider),  0.2f));
        } else {
            paint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        }

        canvas.drawLine(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getPaddingTop(), paint);
    }

    public void setForceDarkTheme(boolean forceDarkTheme) {
        this.forceDarkTheme = forceDarkTheme;
    }
}
