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
package com.nostra13.universalimageloader.core.process;

import android.graphics.Bitmap;
import com.nostra13.universalimageloader.core.DisplayImageOptions;

/**
 * Makes some processing on {@link Bitmap}. Implementations can apply any changes to original {@link Bitmap}.<br />
 * Implementations have to be thread-safe.
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.8.0
 *        <p>
 *        图片处理接口。可用于对图片预处理(Pre-process Bitmap)和后处理(Post-process Bitmap)。 比如你想要为你的图片添加一个水印，那么可以自己去实现 BitmapProcessor
 *        接口，在DisplayImageOptions中配置
 *        Pre-process阶段预处理图片，设置后存储在文件系统以及内存缓存中的图片都是加了水印后的。如果只希望在显示时改变不动原图片，可以在BitmapDisplayer中处理。
 */
public interface BitmapProcessor {
    /**
     * Makes some processing of incoming bitmap.<br />
     * This method is executing on additional thread (not on UI thread).<br />
     * <b>Note:</b> If this processor is used as {@linkplain DisplayImageOptions.Builder#preProcessor(BitmapProcessor)
     * pre-processor} then don't forget {@linkplain Bitmap#recycle() to recycle} incoming bitmap if you return a new
     * created one.
     * 
     * @param bitmap
     *            Original {@linkplain Bitmap bitmap}
     * @return Processed {@linkplain Bitmap bitmap}
     */
    Bitmap process(Bitmap bitmap);
}
