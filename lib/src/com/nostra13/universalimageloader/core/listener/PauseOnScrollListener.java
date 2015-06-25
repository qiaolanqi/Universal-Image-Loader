/*******************************************************************************
 * Copyright 2011-2013 Sergey Tarasevich
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *******************************************************************************/
package com.nostra13.universalimageloader.core.listener;

import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.GridView;
import android.widget.ListView;
import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Listener-helper for {@linkplain AbsListView list views} ({@link ListView}, {@link GridView}) which can
 * {@linkplain ImageLoader#pause() pause ImageLoader's tasks} while list view is scrolling (touch scrolling and/or
 * fling). It prevents redundant loadings.<br />
 * Set it to your list view's {@link AbsListView#setOnScrollListener(OnScrollListener) setOnScrollListener(...)}.<br />
 * This listener can wrap your custom {@linkplain OnScrollListener listener}.
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.7.0
 * 
 *        实际调用的是ImageLoader#resume()和pause()方法实现暂停加载图片
 *        <p>
 *        可在 View 滚动过程中暂停图片加载的 Listener，实现了 OnScrollListener 接口。 它的好处是防止滚动中不必要的图片加载，比如快速滚动不希望滚动中的图片加载。在 ListView 或
 *        GridView 中 item 加载图片最好使用它，简单的一行代码:gridView.setOnScrollListener(new
 *        PauseOnScrollListener(ImageLoader.getInstance(), false, true));
 */
public class PauseOnScrollListener implements OnScrollListener {

    private ImageLoader imageLoader;
    // 触摸滑动(手指依然在屏幕上)过程中是否暂停图片加载。
    private final boolean pauseOnScroll;
    // 甩指滚动(手指已离开屏幕)过程中是否暂停图片加载。
    private final boolean pauseOnFling;
    // 自定义的 OnScrollListener 接口，适用于 View 原来就有自定义 OnScrollListener 情况设置。
    private final OnScrollListener externalListener;

    /**
     * Constructor
     * 
     * @param imageLoader
     *            {@linkplain ImageLoader} instance for controlling
     * @param pauseOnScroll
     *            Whether {@linkplain ImageLoader#pause() pause ImageLoader} during touch scrolling
     * @param pauseOnFling
     *            Whether {@linkplain ImageLoader#pause() pause ImageLoader} during fling
     */
    public PauseOnScrollListener(ImageLoader imageLoader, boolean pauseOnScroll, boolean pauseOnFling) {
        this(imageLoader, pauseOnScroll, pauseOnFling, null);
    }

    /**
     * Constructor
     * 
     * @param imageLoader
     *            {@linkplain ImageLoader} instance for controlling
     * @param pauseOnScroll
     *            Whether {@linkplain ImageLoader#pause() pause ImageLoader} during touch scrolling
     * @param pauseOnFling
     *            Whether {@linkplain ImageLoader#pause() pause ImageLoader} during fling
     * @param customListener
     *            Your custom {@link OnScrollListener} for {@linkplain AbsListView list view} which also will be get
     *            scroll events
     */
    public PauseOnScrollListener(ImageLoader imageLoader, boolean pauseOnScroll, boolean pauseOnFling,
            OnScrollListener customListener) {
        this.imageLoader = imageLoader;
        this.pauseOnScroll = pauseOnScroll;
        this.pauseOnFling = pauseOnFling;
        externalListener = customListener;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        switch (scrollState) {
        case OnScrollListener.SCROLL_STATE_IDLE:
            imageLoader.resume();
            break;
        case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
            if (pauseOnScroll) {
                imageLoader.pause();
            }
            break;
        case OnScrollListener.SCROLL_STATE_FLING:
            if (pauseOnFling) {
                imageLoader.pause();
            }
            break;
        }
        if (externalListener != null) {
            externalListener.onScrollStateChanged(view, scrollState);
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (externalListener != null) {
            externalListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }
}
