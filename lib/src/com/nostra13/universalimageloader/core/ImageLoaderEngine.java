/*******************************************************************************
 * Copyright 2011-2014 Sergey Tarasevich
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
package com.nostra13.universalimageloader.core;

import android.view.View;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.FlushedInputStream;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link ImageLoader} engine which responsible for {@linkplain LoadAndDisplayImageTask display task} execution.
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.7.1<br>
 *        任务分发器
 */
class ImageLoaderEngine {
    // ImageLoader的配置信息，可包括图片最大尺寸、线程池、缓存、下载器、解码器等等。
    final ImageLoaderConfiguration configuration;
    /**
     * 用于执行从源获取图片任务的 Executor，为configuration中的
     * taskExecutor，如果为null，则会调用DefaultConfigurationFactory.createExecutor(…)根据配置返回一个默认的线程池。
     */
    private Executor taskExecutor;
    /**
     * 用于执行从缓存获取图片任务的 Executor，为configuration中的
     * taskExecutorForCachedImages，如果为null，则会调用DefaultConfigurationFactory.createExecutor(…)根据配置返回一个默认的线程池。
     */
    private Executor taskExecutorForCachedImages;
    /**
     * 任务分发线程池，任务指LoadAndDisplayImageTask和ProcessAndDisplayImageTask，因为只需要分发给上面的两个 Executor
     * 去执行任务，不存在较耗时或阻塞操作，所以用无并发数(Int 最大值)限制的线程池即可。
     */
    private Executor taskDistributor;
    /**
     * ImageAware与内存缓存 key 对应的 map，key 为ImageAware的 id，value 为内存缓存的 key。
     */
    private final Map<Integer, String> cacheKeysForImageAwares = Collections
            .synchronizedMap(new HashMap<Integer, String>());
    // 图片正在加载的重入锁 map，key 为图片的 uri，value 为标识其正在加载的重入锁。
    private final Map<String, ReentrantLock> uriLocks = new WeakHashMap<String, ReentrantLock>();
    // 是否被暂停。如果为true，则所有新的加载或显示任务都会等待直到取消暂停(为false)。
    private final AtomicBoolean paused = new AtomicBoolean(false);
    // 是否不允许访问网络，如果为true，通过ImageLoadingListener.onLoadingFailed(…)获取图片，则所有不在缓存中需要网络访问的请求都会失败，返回失败原因为网络访问被禁止。
    private final AtomicBoolean networkDenied = new AtomicBoolean(false);
    // 是否是慢网络情况，如果为true，则自动调用SlowNetworkImageDownloader下载图片。
    private final AtomicBoolean slowNetwork = new AtomicBoolean(false);
    // 暂停的等待锁，可在engine被暂停后调用这个锁等待。
    private final Object pauseLock = new Object();

    ImageLoaderEngine(ImageLoaderConfiguration configuration) {
        this.configuration = configuration;

        taskExecutor = configuration.taskExecutor;
        taskExecutorForCachedImages = configuration.taskExecutorForCachedImages;

        taskDistributor = DefaultConfigurationFactory.createTaskDistributor();
    }

    /**
     * 添加一个LoadAndDisplayImageTask。直接用taskDistributor执行一个 Runnable，在 Runnable
     * 内部根据图片是否被磁盘缓存过确定使用taskExecutorForCachedImages还是taskExecutor执行该 task。
     */
    void submit(final LoadAndDisplayImageTask task) {
        taskDistributor.execute(new Runnable() {
            @Override
            public void run() {
                File image = configuration.diskCache.get(task.getLoadingUri());
                boolean isImageCachedOnDisk = image != null && image.exists();
                initExecutorsIfNeed();
                if (isImageCachedOnDisk) {// 磁盘是否缓存过
                    taskExecutorForCachedImages.execute(task);
                } else {
                    taskExecutor.execute(task);
                }
            }
        });
    }

    /** 添加一个ProcessAndDisplayImageTask。直接用taskExecutorForCachedImages执行该 task。 */
    void submit(ProcessAndDisplayImageTask task) {
        initExecutorsIfNeed();
        taskExecutorForCachedImages.execute(task);
    }

    private void initExecutorsIfNeed() {
        if (!configuration.customExecutor && ((ExecutorService) taskExecutor).isShutdown()) {
            taskExecutor = createTaskExecutor();
        }
        if (!configuration.customExecutorForCachedImages && ((ExecutorService) taskExecutorForCachedImages)
                .isShutdown()) {
            taskExecutorForCachedImages = createTaskExecutor();
        }
    }

    /**
     * 调用DefaultConfigurationFactory.createExecutor(…)创建一个线程池。
     * 
     * @return
     */
    private Executor createTaskExecutor() {
        return DefaultConfigurationFactory
                .createExecutor(configuration.threadPoolSize, configuration.threadPriority,
                        configuration.tasksProcessingType);
    }

    /**
     * Returns URI of image which is loading at this moment into passed
     * {@link com.nostra13.universalimageloader.core.imageaware.ImageAware}<br>
     * 得到某个imageAware正在加载的图片 uri。
     */
    String getLoadingUriForView(ImageAware imageAware) {
        return cacheKeysForImageAwares.get(imageAware.getId());
    }

    /**
     * Associates <b>memoryCacheKey</b> with <b>imageAware</b>. Then it helps to define image URI is loaded into View at
     * exact moment.<br>
     * 准备开始一个Task。向cacheKeysForImageAwares中插入ImageAware的 id 和图片在内存缓存中的 key。
     */
    void prepareDisplayTaskFor(ImageAware imageAware, String memoryCacheKey) {
        cacheKeysForImageAwares.put(imageAware.getId(), memoryCacheKey);
    }

    /**
     * Cancels the task of loading and displaying image for incoming <b>imageAware</b>.
     * 
     * @param imageAware
     *            {@link com.nostra13.universalimageloader.core.imageaware.ImageAware} for which display task will be
     *            cancelled<br>
     *            取消一个显示任务。从cacheKeysForImageAwares中删除ImageAware对应元素。
     */
    void cancelDisplayTaskFor(ImageAware imageAware) {
        cacheKeysForImageAwares.remove(imageAware.getId());
    }

    /**
     * Denies or allows engine to download images from the network.<br />
     * <br />
     * If downloads are denied and if image isn't cached then
     * {@link ImageLoadingListener#onLoadingFailed(String, View, FailReason)} callback will be fired with
     * {@link FailReason.FailType#NETWORK_DENIED}
     * 
     * @param denyNetworkDownloads
     *            pass <b>true</b> - to deny engine to download images from the network; <b>false</b> - to allow engine
     *            to download images from network.
     *            <p>
     *            设置是否不允许网络访问。
     */
    void denyNetworkDownloads(boolean denyNetworkDownloads) {
        networkDenied.set(denyNetworkDownloads);
    }

    /**
     * Sets option whether ImageLoader will use {@link FlushedInputStream} for network downloads to handle <a
     * href="http://code.google.com/p/android/issues/detail?id=6066">this known problem</a> or not.
     * 
     * @param handleSlowNetwork
     *            pass <b>true</b> - to use {@link FlushedInputStream} for network downloads; <b>false</b> - otherwise.
     *            <p>
     *            设置是否慢网络情况。
     */
    void handleSlowNetwork(boolean handleSlowNetwork) {
        slowNetwork.set(handleSlowNetwork);
    }

    /**
     * Pauses engine. All new "load&display" tasks won't be executed until ImageLoader is {@link #resume() resumed}.<br
	 * />
     * Already running tasks are not paused.<br>
     * 暂停图片加载任务。所有新的加载或显示任务都会等待直到取消暂停(为false)。
     */
    void pause() {
        paused.set(true);
    }

    /**
     * Resumes engine work. Paused "load&display" tasks will continue its work.<br>
     * 继续图片加载任务。
     */
    void resume() {
        paused.set(false);
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    /**
     * Stops engine, cancels all running and scheduled display image tasks. Clears internal data. <br />
     * <b>NOTE:</b> This method doesn't shutdown
     * {@linkplain com.nostra13.universalimageloader.core.ImageLoaderConfiguration.Builder#taskExecutor(java.util.concurrent.Executor)
     * custom task executors} if you set them.<br>
     * 暂停所有加载和显示图片任务并清除这里的内部属性值。
     */
    void stop() {
        if (!configuration.customExecutor) {
            ((ExecutorService) taskExecutor).shutdownNow();
        }
        if (!configuration.customExecutorForCachedImages) {
            ((ExecutorService) taskExecutorForCachedImages).shutdownNow();
        }

        cacheKeysForImageAwares.clear();
        uriLocks.clear();
    }

    /**
     * taskDistributor立即执行某个任务。
     */
    void fireCallback(Runnable r) {
        taskDistributor.execute(r);
    }

    /**
     * 得到某个 uri 的重入锁，如果不存在则新建。
     * 
     * @param uri
     * @return
     */
    ReentrantLock getLockForUri(String uri) {
        ReentrantLock lock = uriLocks.get(uri);
        if (lock == null) {
            lock = new ReentrantLock();
            uriLocks.put(uri, lock);
        }
        return lock;
    }

    AtomicBoolean getPause() {
        return paused;
    }

    Object getPauseLock() {
        return pauseLock;
    }

    boolean isNetworkDenied() {
        return networkDenied.get();
    }

    boolean isSlowNetwork() {
        return slowNetwork.get();
    }
}
