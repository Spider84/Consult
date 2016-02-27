package vc.spider.consult;

/**
 * Created by User on 14.08.2015.
 */

import android.widget.ProgressBar;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;

import java.lang.reflect.Array;

public class TripBar extends ProgressBar
{
    private Paint m_BoldLine;
    private Paint m_SmallLine;

    //================================================================

    public TripBar(Context context)
    {
        super(context);

        initialize();
    }

    public TripBar(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        initialize();
    }

    public TripBar(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);

        initialize();
    }

    //================================================================

    private void initialize()
    {
        m_BoldLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        m_BoldLine.setColor(Color.WHITE);
        m_BoldLine.setStrokeWidth(4.0f);

        m_SmallLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        m_SmallLine.setColor(getResources().getColor(R.color.custom_progress_blue_header));
        m_SmallLine.setStrokeWidth(2.0f);
    }

    //================================================================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    //================================================================

    @Override
    protected synchronized void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);

        int max = getMax();
        int width = getWidth();
        int height = getHeight();
        int progress = Math.max(getProgress(), getSecondaryProgress());

        // what is the distance between each tick mark?
        float increment = (float) getHeight() / (max/33.3f);

        // draw the tick marks
        for (int i = (int)(max/33.3)-1; i > 0; i--)
        {
            float y = i * increment;

            if (y<height-((height*progress)/max)) break;

            // make every 5th tick mark bigger
            if ((i % 3) == 0)
                canvas.drawLine(0, y, width, y, m_BoldLine);
            else
                canvas.drawLine(0, y, width, y, m_SmallLine);
        }
    }

    //================================================================

    @Override
    public synchronized void setProgress(int progress)
    {
        if (getMax()<progress) setMax(progress);
        super.setProgress(progress);
    }

    @Override
    public synchronized void setSecondaryProgress(int progress)
    {
        if (getMax()<progress) setMax(progress);
        super.setSecondaryProgress(progress);
    }
}
