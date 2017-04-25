package com.cjt.slide;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Scroller;

public class SlidingMenu extends ViewGroup {

    /* 侧滑模式 */
    public final static int NORMAL = 0;
    public final static int DRAWER = 1;
    public final static int QQ = 2;

    /**
     * 最低触发菜单动画效果水平速率
     */
    public final static int MIN_VELOCITY = 500;

    /**
     * 内容透明度的最低程度，默认值是0.7
     */
    private float minAlpha = 0.7f;

    /**
     * 菜单布局占父布局的百分比，默认值是0.8
     */
    private float menuWidthRate = 0.8f;

    /**
     * 仿QQ菜单缩放视图比例，默认值是0.7
     */
    private float scaleRate = 0.7f;

    /**
     * 侧滑模式，上述三种可选
     */
    private int slidingMode;

    /**
     * 显示隐藏菜单动画默认时间
     */
    private int aniTime = 250;

    /**
     * 平滑滚动帮助类
     */
    private Scroller scroller;

    private VelocityTracker mVelocityTracker;

    private boolean once = false;

    private ViewGroup leftMenu, rightContent;

    private LinearLayout mask;

    public SlidingMenu(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingMenu(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // 获取xml定义的属性（如果存在的话）
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlidingMenu);
        for (int i = 0; i < a.getIndexCount(); i++) {
            int attr = a.getIndex(i);
            if (attr == R.styleable.SlidingMenu_menu_width_rate){
                menuWidthRate = a.getFloat(attr, menuWidthRate);
            } else if (attr == R.styleable.SlidingMenu_sliding_mode){
                slidingMode = a.getInteger(attr, NORMAL);
            } else if (attr == R.styleable.SlidingMenu_content_alpha){
                minAlpha = a.getFloat(attr, minAlpha);
            } else if (attr == R.styleable.SlidingMenu_scale_rate){
                scaleRate = a.getFloat(attr, scaleRate);
            } else if (attr == R.styleable.SlidingMenu_animator_time){
                aniTime = a.getInt(attr, aniTime);
            }
        }
        a.recycle();
        scroller = new Scroller(context);
        mask = new Mask(context);
        // 使得该自定义布局能够触发完整的事件过程
        setClickable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!once) {
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            rightContent = (ViewGroup) getChildAt(0);
            leftMenu = (ViewGroup) getChildAt(1);
            // 计算左菜单的宽度
            int leftMenuWidth = (int) (widthSize * menuWidthRate);
            // 分别测量左菜单，右边内容布局的大小
            leftMenu.measure(MeasureSpec.makeMeasureSpec(leftMenuWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
            rightContent.measure(widthMeasureSpec, heightMeasureSpec);
            // 避免在drawer情况事件穿透menu布局到达content
            leftMenu.setClickable(true);
            mask.measure(widthMeasureSpec, heightMeasureSpec);
            addView(mask);
            once = true;
        }
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (changed){
            // 布置布局位置
            layout(l, t, r, b);
            int leftWidth = leftMenu.getMeasuredWidth();
            leftMenu.layout(-leftWidth, t, l, b);
            rightContent.layout(l, t, r, b);
            mask.layout(l, t, r, b);
            setAnimateViewPivot();
        }
    }

    private float lastX;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        createVelocityTracker(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                break;
            case MotionEvent.ACTION_MOVE:
                // 注意这里的偏移量是上一次的坐标减去当前的坐标值
                int offsetX = (int) (lastX - event.getX());
                // 越界判断处理
                if (getScrollX() + offsetX < -leftMenu.getMeasuredWidth() || getScrollX() + offsetX > 0){
                    break;
                }
                scrollBy(offsetX, 0);
                animator();
                lastX = event.getX();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastX = 0;
                int menuWidth = leftMenu.getWidth();
                int velocity = getScrollVelocity();
                if (-getScrollX() >= menuWidth / 2){
                    if (velocity < -MIN_VELOCITY){
                        smoothScrollTo(0, 0);
                    } else {
                        smoothScrollTo(-menuWidth, 0);
                    }
                } else {
                    if (velocity > MIN_VELOCITY){
                        smoothScrollTo(-menuWidth, 0);
                    } else {
                        smoothScrollTo(0, 0);
                    }
                }
                recycleVelocityTracker();
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    public void smoothScrollTo(int tarX, int tarY) {
        int offsetX = tarX - getScrollX();
        int offsetY = tarY - getScrollY();
        smoothScrollBy(offsetX, offsetY);
    }

    //调用此方法设置滚动的相对偏移
    public void smoothScrollBy(int offsetX, int offsetY) {
        //设置scroller的滚动偏移量
        scroller.startScroll(getScrollX(), getScrollY(), offsetX, offsetY, aniTime);
        postInvalidate();
    }

    @Override
    public void computeScroll() {
        //先判断scroller滚动是否完成
        if (scroller.computeScrollOffset()) {
            //这里调用View的scrollTo()完成实际的滚动
            scrollTo(scroller.getCurrX(), scroller.getCurrY());
            animator();
            postInvalidate();
        } else {
            // 视图滚动完成触发，当菜单完全隐藏才隐藏遮罩
            if (getScrollX() == 0){
                mask.setVisibility(GONE);
            } else {
                mask.setVisibility(VISIBLE);
            }
        }
        super.computeScroll();
    }

    /**
     * 动画效果
     */
    public void animator(){
        int offsetX = -getScrollX();
        float offRate = offsetX * 1.0f / leftMenu.getWidth();
        float alphaDegree = (minAlpha - 1) * offRate + 1;
        switch (slidingMode){
            case NORMAL:
                rightContent.animate().alpha(alphaDegree).setDuration(0).start();
                break;
            case DRAWER:
                rightContent.animate().translationX(-offsetX).alpha(alphaDegree).setDuration(0).start();
                break;
            case QQ:
                // 菜单动画
                float menuDegree = (1 - scaleRate) * offRate + scaleRate;
                leftMenu.animate().scaleX(menuDegree).scaleY(menuDegree).setDuration(0).start();
                // 内容动画
                float contentDegree = (scaleRate - 1) * offRate + 1;
                rightContent.animate().scaleX(contentDegree).scaleY(contentDegree).alpha(alphaDegree).setDuration(0).start();
                break;
            default:
                break;
        }
    }

    /**
     * 设置需要缩放动画的缩放中心点
     */
    public void setAnimateViewPivot() {
        leftMenu.setPivotX(leftMenu.getWidth());
        leftMenu.setPivotY(leftMenu.getHeight() / 2);
        rightContent.setPivotX(0);
        rightContent.setPivotY(rightContent.getHeight() / 2);
    }

    /**
     * 初始化VelocityTracker对象，并将触摸滑动事件加入到VelocityTracker当中
     */
    private void createVelocityTracker(MotionEvent event) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
    }

    /**
     * 获取手指在content界面滑动的速度
     */
    private int getScrollVelocity() {
        mVelocityTracker.computeCurrentVelocity(1000);
        return (int) mVelocityTracker.getXVelocity();
    }

    /**
     * 回收VelocityTracker对象。
     */
    private void recycleVelocityTracker() {
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    /**
     * 主界面遮罩，处理事件
     */
    class Mask extends LinearLayout implements OnClickListener{

        public Mask(Context context) {
            super(context);
            setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            ViewGroup parent = (ViewGroup)getParent();
            if (parent.getScrollX() == -leftMenu.getWidth()){
                smoothScrollTo(0, 0);
            }
        }
    }
}