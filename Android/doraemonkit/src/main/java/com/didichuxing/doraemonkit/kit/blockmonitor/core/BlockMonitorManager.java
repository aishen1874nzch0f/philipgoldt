package com.didichuxing.doraemonkit.kit.blockmonitor.core;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.blankj.utilcode.util.ActivityUtils;
import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.TimeUtils;
import com.didichuxing.doraemonkit.DoraemonKit;
import com.didichuxing.doraemonkit.R;
import com.didichuxing.doraemonkit.constant.BundleKey;
import com.didichuxing.doraemonkit.constant.DokitConstant;
import com.didichuxing.doraemonkit.constant.FragmentIndex;
import com.didichuxing.doraemonkit.kit.blockmonitor.BlockMonitorFragment;
import com.didichuxing.doraemonkit.kit.blockmonitor.bean.BlockInfo;
import com.didichuxing.doraemonkit.kit.health.AppHealthInfoUtil;
import com.didichuxing.doraemonkit.kit.health.model.AppHealthInfo;
import com.didichuxing.doraemonkit.kit.timecounter.TimeCounterManager;
import com.didichuxing.doraemonkit.ui.UniversalActivity;
import com.didichuxing.doraemonkit.util.LogHelper;
import com.didichuxing.doraemonkit.util.NotificationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Request;
import okhttp3.Response;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

/**
 * @desc: 卡顿检测管理类
 */
public class BlockMonitorManager {
    private static final String TAG = "BlockMonitorManager";
    private static final int MAX_SIZE = 50;


    private static class Holder {
        private static BlockMonitorManager INSTANCE = new BlockMonitorManager();
    }

    private boolean mIsRunning;
    private MonitorCore mMonitorCore;
    private Context mContext;
    private List<BlockInfo> mBlockInfoList = Collections.synchronizedList(new ArrayList<BlockInfo>());
    private OnBlockInfoUpdateListener mOnBlockInfoUpdateListener;


    public static BlockMonitorManager getInstance() {
        return BlockMonitorManager.Holder.INSTANCE;
    }

    private BlockMonitorManager() {

    }

    public void start() {
        if (mIsRunning) {
            LogHelper.i(TAG, "start when manager is running");
            return;
        }
        if (DoraemonKit.APPLICATION == null) {
            LogHelper.e(TAG, "start fail, context is null");
            return;
        }
        // 卡顿检测和跳转耗时统计都使用了Printer的方式，无法同时工作
        TimeCounterManager.get().stop();
        mContext = DoraemonKit.APPLICATION.getApplicationContext();
        if (mMonitorCore == null) {
            mMonitorCore = new MonitorCore();
        }
        mIsRunning = true;
        Looper.getMainLooper().setMessageLogging(mMonitorCore);
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public void stop() {
        if (!mIsRunning) {
            LogHelper.i(TAG, "stop when manager is not running");
            return;
        }
        Looper.getMainLooper().setMessageLogging(null);
        if (mMonitorCore != null) {
            mMonitorCore.shutDown();
            mMonitorCore = null;
        }
        NotificationUtils.cancelNotification(mContext, NotificationUtils.ID_SHOW_BLOCK_NOTIFICATION);
        mIsRunning = false;
        mContext = null;
    }

    public void setOnBlockInfoUpdateListener(OnBlockInfoUpdateListener onBlockInfoUpdateListener) {
        mOnBlockInfoUpdateListener = onBlockInfoUpdateListener;
    }

    /**
     * 动态添加卡顿信息到appHealth
     *
     * @param blockInfo
     */
    private void addBlockInfoInAppHealth(@NonNull BlockInfo blockInfo) {
        try {
            String activityName = ActivityUtils.getTopActivity().getClass().getCanonicalName();
            AppHealthInfo.DataBean.BlockBean blockBean = new AppHealthInfo.DataBean.BlockBean();
            blockBean.setPage(activityName);
            blockBean.setBlockTime("" + TimeUtils.getNowMills());
            blockBean.setDetail(blockInfo.toString());
            AppHealthInfoUtil.getInstance().addBlockInfo(blockBean);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    /**
     * 通知卡顿
     *
     * @param blockInfo
     */
    void notifyBlockEvent(BlockInfo blockInfo) {
        blockInfo.concernStackString = BlockCanaryUtils.concernStackString(mContext, blockInfo);
        blockInfo.time = System.currentTimeMillis();
        if (!TextUtils.isEmpty(blockInfo.concernStackString)) {
            //卡顿 debug模式下会造成卡顿
            if (DokitConstant.APP_HEALTH_RUNNING && !Debug.isDebuggerConnected()) {
                addBlockInfoInAppHealth(blockInfo);
            }
            showNotification(blockInfo);
            if (mBlockInfoList.size() > MAX_SIZE) {
                mBlockInfoList.remove(0);
            }
            mBlockInfoList.add(blockInfo);
            if (mOnBlockInfoUpdateListener != null) {
                mOnBlockInfoUpdateListener.onBlockInfoUpdate(blockInfo);
            }
        }

    }

    private void showNotification(BlockInfo info) {
        String contentTitle = mContext.getString(R.string.dk_block_class_has_blocked, info.timeStart);
        String contentText = mContext.getString(R.string.dk_block_notification_message);
        Intent intent = new Intent(mContext, UniversalActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(BundleKey.FRAGMENT_INDEX, FragmentIndex.FRAGMENT_BLOCK_MONITOR);
        intent.putExtra(BlockMonitorFragment.KEY_JUMP_TO_LIST, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 1, intent, FLAG_UPDATE_CURRENT);
        NotificationUtils.setInfoNotification(mContext, NotificationUtils.ID_SHOW_BLOCK_NOTIFICATION,
                contentTitle, contentText, contentText, pendingIntent);
    }

    public List<BlockInfo> getBlockInfoList() {
        return mBlockInfoList;
    }
}
