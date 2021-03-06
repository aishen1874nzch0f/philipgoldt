package com.didichuxing.doraemonkit.widget.easyrefresh;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;


import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Scroller;

import androidx.core.view.MotionEventCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.didichuxing.doraemonkit.widget.easyrefresh.exception.ERVHRuntimeException;
import com.didichuxing.doraemonkit.widget.easyrefresh.view.SimpleLoadMoreView;
import com.didichuxing.doraemonkit.widget.easyrefresh.view.SimpleRefreshHeaderView;


/**
 * Created by guanaj on 16/9/2.
 */
public class EasyRefreshLayout extends ViewGroup {
    private static final String TAG = "EsayRefreshLayout";
    private static int SCROLL_TO_REFRESH_DURATION = 250;
    private static int SCROLL_TO_TOP_DURATION = 800;
    private static final int INVALID_POINTER = -1;
    private static final float DRAG_RATE = 1.0f;
    private static final int START_POSITION = 0;
    private static long SHOW_COMPLETED_TIME = 500;
    private static long SCROLL_TO_LOADING_DURATION = 500;
    private static long SHOW_SCROLL_DOWN_DURATION = 300;
    private double pull_resistance = 2.0f;

    private State state = State.RESET;

    private boolean isEnablePullToRefresh = true;
    private boolean isRefreshing;


    private int touchSlop;
    private View refreshHeaderView;
    private int currentOffsetTop;

    private View contentView;
    private boolean hasMeasureHeaderView = false;
    private int headerViewHight;
    private int totalDragDistance;
    private int activePointerId;
    private boolean isTouch;
    private boolean hasSendCancelEvent;
    private boolean isBeginDragged;
    private int lastOffsetTop;
    private float lastMotionX;
    private float lastMotionY;
    private float initDownY;
    private float initDownX;
    private MotionEvent lastEvent;
    private AutoScroll autoScroll;
    private boolean isAutoRefresh;
    private EasyEvent easyEvent;


    private RecyclerView mRecyclerView;
    boolean isCanLoad = false;
    private LayoutInflater mInflater;
    private boolean isLoading = false;
    private View mLoadMoreView;
    private boolean isLoadingFail = false;
    private float offsetY;
    private float yDiff;
    private float mDistance;
    //????????????????????????
    //1??????????????????
    //2???????????????????????????
    //3??????????????????????????????
    private LoadModel loadMoreModel = LoadModel.COMMON_MODEL;
    //???????????????item??????????????????
    private int advanceCount = 0;

    public EasyRefreshLayout(Context context) {
        this(context, null);

    }

    public EasyRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initParameter(context, attrs);

    }

    /**
     * ???????????????????????????
     */
    private Runnable delayToScrollTopRunnable = new Runnable() {
        @Override
        public void run() {
            autoScroll.scrollTo(START_POSITION, SCROLL_TO_TOP_DURATION);
        }
    };
    /**
     * ???????????????????????????
     */
    private Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            isAutoRefresh = true;
            changeState(State.PULL);
            autoScroll.scrollTo(totalDragDistance, SCROLL_TO_REFRESH_DURATION);
        }
    };
    /**
     * ??????????????????????????????view
     */
    private boolean hasMeasureLoadMoreView;

    private int loadMoreViewHeight;
    private boolean isRecycerView;
    private boolean isNotMoreLoading;


    private void initParameter(Context context, AttributeSet attrs) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        /*init an refreshHeadView*/
        View refreshHeadView = getDefaultRefreshView();
        setRefreshHeadView(refreshHeadView);
        View loadMoreView = getDefaultLoadMoreView();
        setLoadMoreView(loadMoreView);
        autoScroll = new AutoScroll();
    }

    //??????????????????
    public void setRefreshHeadView(View headerView) {
        if (headerView != null && headerView != refreshHeaderView)
            removeView(refreshHeaderView);

        /*???????????????????????????*/
        LayoutParams layoutParams = headerView.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            headerView.setLayoutParams(layoutParams);
        }

        /*???????????????headerView*/
        refreshHeaderView = headerView;
        addView(refreshHeaderView);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //?????????????????????
//        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
//        System.out.println(">>>>>parentHeight = "+parentHeight);
//        int expandParentHeight = View.MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE >> 2, View.MeasureSpec.AT_MOST);
//        super.onMeasure(widthMeasureSpec, expandParentHeight);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (contentView == null) {
            initContentView();
        }
        //??????contentView
        if (contentView == null)
            return;



        /*???????????????????????????View*/
        /*???contentView??????????????????*/
        int contentViewWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int contentViewHight = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
        contentView.measure(MeasureSpec.makeMeasureSpec(contentViewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(contentViewHight, MeasureSpec.EXACTLY));


        /*??????headerView*/

        measureChild(refreshHeaderView, widthMeasureSpec, heightMeasureSpec);
        if (!hasMeasureHeaderView) {
            /*headerView??????????????????*/
            hasMeasureHeaderView = true;
            //?????????????????????
            headerViewHight = refreshHeaderView.getMeasuredHeight();
            totalDragDistance = headerViewHight;
        }


        /*??????loadMoreView*/
        measureChild(mLoadMoreView, widthMeasureSpec, heightMeasureSpec);
        if (!hasMeasureLoadMoreView) {
            /*headerView??????????????????*/
            hasMeasureLoadMoreView = true;
            //?????????????????????
            loadMoreViewHeight = mLoadMoreView.getMeasuredHeight();
        }
    }

    private void initContentView() {
        // Don't bother getting the parent height if the parent hasn't been laid
        // out yet.
        if (contentView == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (!child.equals(refreshHeaderView) && !child.equals(mLoadMoreView)) {
                    contentView = child;
                    if (contentView instanceof RecyclerView) {
                        isRecycerView = true;
                    } else {
                        isRecycerView = false;
                    }
                    break;
                }
            }
        }
        if (isRecycerView)
            initERVH();
    }

    @Override
    protected void onLayout(boolean b, int i, int i1, int i2, int i3) {

        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            /*????????????View*/
            throw new RuntimeException("child view can not be empty");
        }

        if (contentView == null) {
            initContentView();
        }
        if (contentView == null) {
            throw new RuntimeException("main content of view can not be empty ");
        }

        final View child = contentView;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop() + currentOffsetTop;
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        /*??????contentView ?????????*/

        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

        // header??????contentView????????????????????????
        int refreshViewWidth = refreshHeaderView.getMeasuredWidth();
        refreshHeaderView.layout((width / 2 - refreshViewWidth / 2),
                -headerViewHight + currentOffsetTop,
                (width / 2 + refreshViewWidth / 2),
                currentOffsetTop);


        //loadMoreView ??????contentView ??????

        int loadMoreViewWidth = mLoadMoreView.getMeasuredWidth();
        int loadL = width / 2 - loadMoreViewWidth / 2;
        int loadT = childHeight;
        int loadR = width / 2 + loadMoreViewWidth / 2;
        int loadB = childHeight + loadMoreViewHeight;
        mLoadMoreView.layout(loadL, loadT, loadR, loadB);

    }

    /*??????????????????*/

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {


        if (isLoading || contentView == null) {
            return super.dispatchTouchEvent(ev);
        }

        //???????????????????????????action
        final int actionMasked = ev.getActionMasked();
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN: {
                mDistance = 0;
                //Log.i(TAG, "ACTION_DOWN");
                //????????????????????????????????????id
                activePointerId = ev.getPointerId(0);
                isTouch = true;
                hasSendCancelEvent = false;
                // ??????????????????
                isBeginDragged = false;
                // ?????????contentView???????????????
                lastOffsetTop = currentOffsetTop;
                currentOffsetTop = contentView.getTop();
                // ????????????????????????
                initDownX = lastMotionX = ev.getX(0);
                initDownY = lastMotionY = ev.getY(0);
                autoScroll.stop();
                removeCallbacks(delayToScrollTopRunnable);
                removeCallbacks(autoRefreshRunnable);
                super.dispatchTouchEvent(ev);
                //????????????????????????,?????????view??????????????????????????????????????????????????????????????????
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (activePointerId == INVALID_POINTER) {
                    // Log.e(TAG, "Got ACTION_MOVE event but don't have an active pointer id.");
                    return super.dispatchTouchEvent(ev);
                }
                autoScroll.stop();

                // ????????????move??????
                lastEvent = ev;
                float x = ev.getX(MotionEventCompat.findPointerIndex(ev, activePointerId));
                float y = ev.getY(MotionEventCompat.findPointerIndex(ev, activePointerId));
                float xDiff = x - lastMotionX;
                yDiff = y - lastMotionY;
                mDistance = mDistance + yDiff;
                offsetY = yDiff * DRAG_RATE;
                lastMotionX = x;
                lastMotionY = y;
                if (Math.abs(xDiff) > touchSlop /*&& isInterceptMoveEvent*/) {
                    /*???????????????*/
                    break;
                }

                if (!isBeginDragged && Math.abs(y - initDownY) > touchSlop) {
                    isBeginDragged = true;
                }
                if (isBeginDragged) {
                    boolean isMoveHeadDown = offsetY > 0; // ???
                    boolean canMoveHeadDown = !canChildScrollUp();
                    boolean isMoveHeadUp = !isMoveHeadDown;     // ???
                    boolean canMoveHeadUp = currentOffsetTop > START_POSITION;

                    // ????????????????????????
                    if ((isMoveHeadDown && canMoveHeadDown) || (isMoveHeadUp && canMoveHeadUp)) {
                        moveSpinner(offsetY);
                        return true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_CANCEL:
                // Log.i(TAG, "ACTION_CANCEL");
            case MotionEvent.ACTION_UP: {
                // Log.i(TAG, "ACTION_UP");
                if (currentOffsetTop > START_POSITION) {
                    finishSpinner();
                }
                isTouch = false;
                activePointerId = INVALID_POINTER;
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                // Log.i(TAG, "ACTION_POINTER_DOWN");
                int pointerIndex = MotionEventCompat.getActionIndex(ev);
                if (pointerIndex < 0) {
                    //   Log.e(TAG, "Got ACTION_POINTER_DOWN event but have an invalid action index.");
                    return super.dispatchTouchEvent(ev);
                }
                lastMotionX = ev.getX(pointerIndex);
                lastMotionY = ev.getY(pointerIndex);
                activePointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // Log.i(TAG, "ACTION_POINTER_UP");
                onSecondaryPointerUp(ev);
                lastMotionY = ev.getY(ev.findPointerIndex(activePointerId));
                lastMotionX = ev.getX(ev.findPointerIndex(activePointerId));
                break;
            }
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);

    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        /*???????????????????????????*/
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        // Log.i(TAG, "pointerIndex:" + pointerIndex);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);
        if (pointerId == activePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            lastMotionY = ev.getY(newPointerIndex);
            lastMotionX = ev.getX(newPointerIndex);
            activePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
        }
    }

    private void moveSpinner(float offsetY) {
        if (!isEnablePullToRefresh) {
            return;
        }
        int offset = Math.round(offsetY);
        if (offset == 0) {
            return;
        }

        // ??????cancel?????????child
        if (!hasSendCancelEvent && isTouch && currentOffsetTop > START_POSITION) {
            sendCancelEvent();
            hasSendCancelEvent = true;
        }
        int nextOffsetTop = Math.max(0, currentOffsetTop + offset); // contentView?????????????????????0???????????????
        offset = nextOffsetTop - currentOffsetTop;

        // y = x - (x/2)^2
        float extraOS = nextOffsetTop - totalDragDistance;
        float slingshotDist = totalDragDistance;
        float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, slingshotDist * 2) / slingshotDist);
        float tensionPercent = (float) (tensionSlingshotPercent - Math.pow(tensionSlingshotPercent / this.pull_resistance, 2));

        if (offset > 0) { // ??????????????????????????????
            offset = (int) (offset * (1f - tensionPercent));
            nextOffsetTop = Math.max(0, currentOffsetTop + offset);
        }
        // 1. ???RESET?????????????????????????????????header??????????????????????????????PULL
        if (state == State.RESET && currentOffsetTop == START_POSITION && nextOffsetTop > 0) {
            if (isNotMoreLoading || isLoadingFail)
                closeLoadView();
            changeState(State.PULL);
        }

        // 2. ???PULL??????COMPLETE????????????header????????????????????????????????????RESET


        if (currentOffsetTop > START_POSITION && nextOffsetTop <= START_POSITION) {
            //  Log.i(TAG, "2--contentViewY:" + nextOffsetTop + "--START_POSITION:" + START_POSITION);

            if (state == State.PULL || state == State.COMPLETE) {
                changeState(State.RESET);
            }
        }

        // 3. ???????????????????????????????????????(????????????)??????????????????????????????, ???????????????PULL?????????????????????LOADING????????????????????????????????????autoScroll

        if (state == State.PULL && !isTouch && currentOffsetTop > totalDragDistance && nextOffsetTop <= totalDragDistance) {
            //  Log.i(TAG, "3--contentViewY:" + nextOffsetTop + "--totalDragDistance:" + totalDragDistance);

            autoScroll.stop();

            changeState(State.REFRESHING);
            if (easyEvent != null) {
                isRefreshing = true;
                easyEvent.onRefreshing();
            }
            // ??????????????????targetY <= totalDragDistance????????????????????????????????????????????????????????????????????????????????????change
            int adjustOffset = totalDragDistance - nextOffsetTop;
            offset += adjustOffset;
        }

        setTargetOffsetTopAndBottom(offset);
        // ???????????????header????????????????????????
        if (refreshHeaderView instanceof IRefreshHeader) {
            ((IRefreshHeader) refreshHeaderView)
                    .onPositionChange(currentOffsetTop, lastOffsetTop, totalDragDistance, isTouch, state);
        }


    }

    private void finishSpinner() {
        if (state == State.REFRESHING) {
            if (currentOffsetTop > totalDragDistance) {
                autoScroll.scrollTo(totalDragDistance, SCROLL_TO_REFRESH_DURATION);
            }
        } else {
            autoScroll.scrollTo(START_POSITION, SCROLL_TO_TOP_DURATION);
        }
    }

    private boolean canChildScrollUp() {
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (contentView instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) contentView;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 || absListView.getChildAt(0)
                        .getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(contentView, -1) || contentView.getScrollY() > 0;
            }
        } else {
            /*return true can  swipe up*/
            return ViewCompat.canScrollVertically(contentView, -1);
        }
    }

    private void setTargetOffsetTopAndBottom(int offset) {
        if (offset == 0) {
            return;
        }

        contentView.offsetTopAndBottom(offset);
        refreshHeaderView.offsetTopAndBottom(offset);
        lastOffsetTop = currentOffsetTop;
        currentOffsetTop = contentView.getTop();
        invalidate();
    }

    /**
     * ACTION??????MOVE??????????????????,???View???????????????????????????
     * ???????????????DOWN????????????
     */
    private void sendCancelEvent() {
        if (lastEvent == null) {
            return;
        }
        // Log.i(TAG, "start sendCancelEvent");
        MotionEvent ev = MotionEvent.obtain(lastEvent);
        ev.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(ev);
    }

    private void changeState(State state) {
        // Toast.makeText(getContext(), state.toString(), Toast.LENGTH_SHORT).show();

        this.state = state;

        IRefreshHeader refreshHeader = this.refreshHeaderView instanceof IRefreshHeader ? ((IRefreshHeader) this.refreshHeaderView) : null;
        if (refreshHeader != null) {
            switch (state) {
                case RESET:
                    refreshHeader.reset();
                    break;
                case PULL:
                    refreshHeader.pull();
                    break;
                case REFRESHING:
                    refreshHeader.refreshing();
                    break;
                case COMPLETE:
                    refreshHeader.complete();
                    break;
            }
        }
    }

    public void refreshComplete() {
        isRefreshing = false;
        changeState(State.COMPLETE);
        // if refresh completed and the contentView at top, change state to reset.
        if (currentOffsetTop == START_POSITION) {
            changeState(State.RESET);
        } else {
            // waiting for a time to show refreshView completed state.
            // at next touch event, remove this runnable
            if (!isTouch) {
                postDelayed(delayToScrollTopRunnable, SHOW_COMPLETED_TIME);
            }
        }
    }

    public void autoRefresh() {
        autoRefresh(500);
    }

    /**
     * ???onCreate?????????autoRefresh?????????View???????????????????????????????????????????????????????????????
     *
     * @param duration ????????????????????????
     */
    public void autoRefresh(long duration) {
        if (state != State.RESET) {
            return;
        }
        postDelayed(autoRefreshRunnable, duration);
    }

    public View getDefaultRefreshView() {
        return new SimpleRefreshHeaderView(getContext());
    }

    private class AutoScroll implements Runnable {
        private Scroller scroller;
        private int lastY;

        public AutoScroll() {
            scroller = new Scroller(getContext());
        }

        @Override
        public void run() {
            boolean finished = !scroller.computeScrollOffset() || scroller.isFinished();
            if (!finished) {
                int currY = scroller.getCurrY();
                int offset = currY - lastY;
                lastY = currY;
                moveSpinner(offset); // ?????????????????????header???contentView
                /*????????????????????????*/
                post(this);
                onScrollFinish(false);
            } else {
                stop();
                onScrollFinish(true);
            }
        }

        public void scrollTo(int to, int duration) {
            int from = currentOffsetTop;
            int distance = to - from;
            stop();
            if (distance == 0) {
                return;
            }
            //???????????????
            scroller.startScroll(0, 0, 0, distance, duration);
            post(this);
        }

        private void stop() {
            removeCallbacks(this);
            if (!scroller.isFinished()) {
                scroller.forceFinished(true);
            }
            lastY = 0;
        }
    }


    /**
     * ???scroll????????????????????????????????????
     *
     * @param isForceFinish ????????????????????????
     */

    private void onScrollFinish(boolean isForceFinish) {
        if (isAutoRefresh && !isForceFinish) {
            isAutoRefresh = false;
            changeState(State.REFRESHING);
            if (easyEvent != null) {
                easyEvent.onRefreshing();
            }
            finishSpinner();
        }
    }

    // ?????????????????????
    public interface OnRefreshListener {
        void onRefreshing();
    }

    public interface EasyEvent extends OnRefreshListener, LoadMoreEvent {

    }

    // ????????????????????????
    public void addEasyEvent(EasyEvent event) {
        if (event == null) {
            throw new ERVHRuntimeException("adapter can not be null");
        }
        this.easyEvent = event;
    }

    public boolean isEnablePullToRefresh() {
        return isEnablePullToRefresh;
    }

    public void setEnablePullToRefresh(boolean enable) {
        isEnablePullToRefresh = enable;
    }

    public boolean isRefreshing() {
        return isRefreshing;

    }

    public void setRefreshing(boolean refreshing) {
        if (refreshing) {
            changeState(State.REFRESHING);
            if (isNotMoreLoading || isLoadingFail) {
                closeLoadView();
            }
        }
        changeState(State.RESET);
    }


    private void initERVH() {
        if (mLoadMoreView == null) {
            getDefaultLoadMoreView();
            setLoadMoreView(mLoadMoreView);
        }
        if (!isRecycerView) {
            return;
        }
        mRecyclerView = (RecyclerView) contentView;
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {


            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
                    if (!isLoading && !isRefreshing && !isLoadingFail && !isNotMoreLoading) {
                        final int lastVisibleItem = getLastVisiBleItem();
                        int totalItemCount = mRecyclerView.getLayoutManager().getItemCount();
                        int totalChildCount = mRecyclerView.getLayoutManager().getChildCount();
                        if (totalChildCount > 0 && lastVisibleItem >= totalItemCount - 1 - advanceCount && totalItemCount >= totalChildCount) {
                            isCanLoad = true;
                        }
                        if (isCanLoad) {
                            isCanLoad = false;
                            isLoading = true;
                            if (easyEvent != null) {
                                easyEvent.onLoadMore();
                            }

                        }
                    }

                }


            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // Log.i(TAG, ">>>>mDistance:" + mDistance);
                // Log.i(TAG, ">>>>touchSlop:" + touchSlop);
                if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
                    return;
                }
                if (Math.abs(mDistance) > touchSlop && mDistance < 0) {
                    if (!isLoading && loadMoreModel == LoadModel.COMMON_MODEL && !isRefreshing && !isLoadingFail && !isNotMoreLoading) {
                        final int lastVisibleItem = getLastVisiBleItem();
                        int totalItemCount = mRecyclerView.getLayoutManager().getItemCount();
                        int totalChildCount = mRecyclerView.getLayoutManager().getChildCount();
                        if (totalChildCount > 0 && lastVisibleItem >= totalItemCount - 1 && totalItemCount >= totalChildCount) {
                            isCanLoad = true;
                        }
                        if (isCanLoad) {
                            isCanLoad = false;
                            isLoading = true;
                            ((ILoadMoreView) mLoadMoreView).reset();

                            mLoadMoreView.measure(0, 0);
                            ((ILoadMoreView) mLoadMoreView).loading();
                            showLoadView();

                        }
                    }
                }
            }
        });


    }

    private void showLoadView() {

        ValueAnimator animator = ValueAnimator.ofInt(0, -mLoadMoreView.getMeasuredHeight());
        animator.setTarget(mLoadMoreView);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            private int lastDs;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final int ds = (int) animation.getAnimatedValue();
                // setTargetOffsetTopAndBottom((ds - lastDs));
                lastDs = ds;
                mLoadMoreView.bringToFront();
                mLoadMoreView.setTranslationY(ds);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
                                 @Override
                                 public void onAnimationEnd(Animator animation) {
                                     super.onAnimationEnd(animation);
                                     if (easyEvent != null) {
                                         easyEvent.onLoadMore();
                                     }
                                 }
                             }

        );
        animator.setDuration(SCROLL_TO_LOADING_DURATION);
        animator.start();


    }

    private void hideLoadView() {
        // setTargetOffsetTopAndBottom( mLoadMoreView.getMeasuredHeight());
        if (mLoadMoreView != null && isRecycerView) {
            ValueAnimator animator = ValueAnimator.ofInt(0, mLoadMoreView.getMeasuredHeight());
            animator.setTarget(mLoadMoreView);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

                private int lastDs;

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {

                    int ds = (int) animation.getAnimatedValue();
                    //  setTargetOffsetTopAndBottom((ds - lastDs));
                    lastDs = ds;
                    mLoadMoreView.bringToFront();
                    mLoadMoreView.setTranslationY(ds);


                }

            });
            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    isLoading = false;

                }

                @Override
                public void onAnimationCancel(Animator animator) {
                    isLoading = false;

                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                    isLoading = false;

                }
            });
            animator.setDuration(SHOW_SCROLL_DOWN_DURATION);
            animator.start();
        }

    }

    public void closeLoadView() {
        if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
            throw new RuntimeException("enableAdance Model cant not called closeLoadView method");

        } else {
            // setTargetOffsetTopAndBottom( mLoadMoreView.getMeasuredHeight());
            if (mLoadMoreView != null && isRecycerView) {
                // setTargetOffsetTopAndBottom(mLoadMoreView.getMeasuredHeight());
                mLoadMoreView.bringToFront();
                mLoadMoreView.setTranslationY(mLoadMoreView.getMeasuredHeight());
                resetLoadMoreState();

            }
        }

    }


    public View getLoadMoreView() {
        return (View) getDefaultLoadMoreView();
    }

    public void setLoadMoreView(View loadMoreView) {
        if (loadMoreView == null)
            throw new ERVHRuntimeException("loadMoreView can not be null");
        if (loadMoreView != null && loadMoreView != mLoadMoreView)
            removeView(mLoadMoreView);

        /*???????????????????????????*/
        LayoutParams layoutParams = loadMoreView.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            loadMoreView.setLayoutParams(layoutParams);
        }
        mLoadMoreView = loadMoreView;
        addView(mLoadMoreView);
        resetLoadMoreState();
        ((ILoadMoreView) mLoadMoreView).reset();

        ((ILoadMoreView) mLoadMoreView).getCanClickFailView().setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLoadingFail && easyEvent != null) {
                    isLoading = true;
                    ((ILoadMoreView) mLoadMoreView).loading();
                    easyEvent.onLoadMore();
                }
            }
        });

    }

    public void loadMoreComplete() {
        if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
            isLoading = false;
        } else if (loadMoreModel == LoadModel.COMMON_MODEL) {
            loadMoreComplete(null);
        }
    }

    @Deprecated
    public void loadMoreComplete(EasyRefreshLayout.Event event) {
        if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
            throw new RuntimeException("enableAdance Model cant not called closeLoadView method");

        } else {
            loadMoreComplete(event, 500);
        }
    }

    @Deprecated
    public void loadMoreComplete(final EasyRefreshLayout.Event event, long delayedTime) {
        if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
            throw new RuntimeException("enableAdance Model cant not called closeLoadView method");

        } else {

            ((ILoadMoreView) mLoadMoreView).loadComplete();

            if (event == null) {
                hideLoadView();
                resetLoadMoreState();
                return;
            }
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    event.complete();
                    hideLoadView();
                    resetLoadMoreState();
                }
            }, delayedTime);
        }
    }

    private void resetLoadMoreState() {
        isCanLoad = false;
        isLoading = false;
        isLoadingFail = false;
        isNotMoreLoading = false;
    }


    public void loadMoreFail() {
        if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
            throw new RuntimeException("enableAdance Model cant not called closeLoadView method");

        } else {
            ((ILoadMoreView) mLoadMoreView).loadFail();
            resetLoadMoreState();
            isLoadingFail = true;
        }
    }

    public void loadNothing() {
        if (loadMoreModel == LoadModel.ADVANCE_MODEL) {
            throw new RuntimeException("enableAdance Model cant not called closeLoadView method");

        } else {
            ((ILoadMoreView) mLoadMoreView).loadNothing();
            resetLoadMoreState();
            isNotMoreLoading = true;
        }
    }

    private View getDefaultLoadMoreView() {
        return new SimpleLoadMoreView(getContext());
    }

    /**
     * ?????????????????????
     */
    private int getLastVisiBleItem() {
        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        int lastVisibleItemPosition = -1;

        int layoutManagerType = 0;
        if (layoutManager instanceof GridLayoutManager) {
            layoutManagerType = 1;
        } else if (layoutManager instanceof LinearLayoutManager) {
            layoutManagerType = 0;
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            layoutManagerType = 2;
        } else {
            throw new RuntimeException(
                    "Unsupported LayoutManager used. Valid ones are LinearLayoutManager, GridLayoutManager and StaggeredGridLayoutManager");
        }

        switch (layoutManagerType) {
            case 0:
                lastVisibleItemPosition = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
                break;
            case 1:
                lastVisibleItemPosition = ((GridLayoutManager) layoutManager).findLastVisibleItemPosition();
                break;
            case 2:
                StaggeredGridLayoutManager staggeredGridLayoutManager = (StaggeredGridLayoutManager) layoutManager;
                int[] lastPositions = new int[staggeredGridLayoutManager.getSpanCount()];
                staggeredGridLayoutManager.findLastVisibleItemPositions(lastPositions);
                lastVisibleItemPosition = findMax(lastPositions);
                break;
        }
        return lastVisibleItemPosition;
    }

    private int findMax(int[] lastPositions) {
        int max = lastPositions[0];
        for (int value : lastPositions) {
            if (value > max) {
                max = value;
            }
        }
        return max;
    }


    public boolean isEnableLoadMore() {
        return loadMoreModel == LoadModel.COMMON_MODEL || loadMoreModel == LoadModel.ADVANCE_MODEL;
    }

    public LoadModel getLoadMoreModel() {
        return loadMoreModel;
    }

    public void setLoadMoreModel(LoadModel loadMoreModel, int advanceCount) {
        this.loadMoreModel = loadMoreModel;
        this.advanceCount = advanceCount;
    }

    public int getAdvanceCount() {
        return advanceCount;
    }

    public void setAdvanceCount(int advanceCount) {
        this.advanceCount = advanceCount;
    }

    /**
     * ??????????????????????????????0
     *
     * @param loadMoreModel
     */
    public void setLoadMoreModel(LoadModel loadMoreModel) {
        this.setLoadMoreModel(loadMoreModel, 0);
    }


    public boolean isLoading() {
        return isLoading;
    }

    public interface LoadMoreEvent {
        /***
         * ????????????
         */
        void onLoadMore();
    }

    public interface Event {
        void complete();

    }

    public long getShowLoadViewAnimatorDuration() {
        return SCROLL_TO_LOADING_DURATION;
    }

    public void setShowLoadViewAnimatorDuration(long scrollToLoadingDuration) {
        SCROLL_TO_LOADING_DURATION = scrollToLoadingDuration;
    }

    public int getScrollToRefreshDuration() {
        return SCROLL_TO_REFRESH_DURATION;
    }

    public void setScrollToRefreshDuration(int scrollToRefreshDuration) {
        SCROLL_TO_REFRESH_DURATION = scrollToRefreshDuration;
    }

    public int getScrollToTopDuration() {
        return SCROLL_TO_TOP_DURATION;
    }

    public void setScrollToTopDuration(int scrollToTopDuration) {
        SCROLL_TO_TOP_DURATION = scrollToTopDuration;
    }

    public long getHideLoadViewAnimatorDuration() {
        return SHOW_COMPLETED_TIME;
    }

    public void setHideLoadViewAnimatorDuration(long showCompletedTime) {
        SHOW_COMPLETED_TIME = showCompletedTime;
    }


    public double getPullResistance() {
        return this.pull_resistance;
    }

    /**
     * Set the pull-down refresh resistance factor
     *
     * @param PullResistance resistance factor
     */
    public void setPullResistance(double PullResistance) {
        this.pull_resistance = PullResistance;
    }
}
