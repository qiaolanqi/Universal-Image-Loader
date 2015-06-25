package com.nostra13.universalimageloader.core.assist.deque;

import java.util.NoSuchElementException;

/**
 * {@link LinkedBlockingDeque} using LIFO algorithm
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @since 1.6.3 <br>
 *        后进先出阻塞队列。重写LinkedBlockingDeque的offer(…) 让LinkedBlockingDeque插入总在最前，而remove()本身始终删除第一个元素，所以就变为了后进先出阻塞队列。
 *        实际一般情况只重写offer(…)函数是不够的，但因为ThreadPoolExecutor默认只用到了BlockingQueue的offer
 *        (…)函数，所以这种简单重写后做为ThreadPoolExecutor的任务队列没问题。
 *        LIFOLinkedBlockingDeque.java包下的LinkedBlockingDeque.java、BlockingDeque.java、Deque.java都是 Java 1.6 源码中的，这里不做分析。
 */
public class LIFOLinkedBlockingDeque<T> extends LinkedBlockingDeque<T> {

    private static final long serialVersionUID = -4114786347960826192L;

    /**
     * Inserts the specified element at the front of this deque if it is possible to do so immediately without violating
     * capacity restrictions, returning <tt>true</tt> upon success and <tt>false</tt> if no space is currently
     * available. When using a capacity-restricted deque, this method is generally preferable to the {@link #addFirst
     * addFirst} method, which can fail to insert an element only by throwing an exception.
     * 
     * @param e
     *            the element to add
     * @throws ClassCastException
     *             {@inheritDoc}
     * @throws NullPointerException
     *             if the specified element is null
     * @throws IllegalArgumentException
     *             {@inheritDoc}
     */
    @Override
    public boolean offer(T e) {
        return super.offerFirst(e);
    }

    /**
     * Retrieves and removes the first element of this deque. This method differs from {@link #pollFirst pollFirst} only
     * in that it throws an exception if this deque is empty.
     * 
     * @return the head of this deque
     * @throws NoSuchElementException
     *             if this deque is empty
     */
    @Override
    public T remove() {
        return super.removeFirst();
    }
}
