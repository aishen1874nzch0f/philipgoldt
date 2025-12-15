package com.didichuxing.doraemonkit.kit.health;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.View;
import android.widget.LinearLayout;

import com.didichuxing.doraemonkit.R;
import com.didichuxing.doraemonkit.ui.base.BaseFragment;

/**
 * 健康体检fragment
 */
public class HealthFragmentChild1 extends BaseFragment {
    LinearLayout mLlBackTop;

    @Override
    protected int onRequestLayout() {
        return R.layout.dk_fragment_health_child1;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLlBackTop = findViewById(R.id.ll_back_top);
        mLlBackTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Fragment parentFragment = getParentFragment();
                if (parentFragment instanceof HealthFragment) {
                    ((HealthFragment) parentFragment).scroll2theTop();
                }
            }
        });
    }


}
