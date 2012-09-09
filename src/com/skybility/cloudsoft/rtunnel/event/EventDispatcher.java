/*$Id: $
 --------------------------------------
  Skybility
 ---------------------------------------
  Copyright By Skybility ,All right Reserved
 * author   date   comment
 * jeremy  2012-8-2  Created
 */
package com.skybility.cloudsoft.rtunnel.event;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.skybility.cloudsoft.rtunnel.listener.EventListener;

public class EventDispatcher<E> extends Thread {
	private static final Logger log = LoggerFactory
			.getLogger(EventDispatcher.class);

	private BlockingQueue<E> eventQueue = new LinkedBlockingQueue<E>();

	private List<EventListener<E>> listeners = new CopyOnWriteArrayList<EventListener<E>>();

	private volatile boolean keep_running = false;

	public EventDispatcher(String threadName) {
		super(threadName);
	}

	public void startDispatch() {
		keep_running = true;
		this.start();
	}

	public void stopDispatch() {
		keep_running = false;
		if (!this.isInterrupted()) {
			this.interrupt();
		}
	}

	@Override
	public void run() {
		while (keep_running) {
			E e = null;
			try {
				e = eventQueue.take();
			} catch (InterruptedException e1) {
				// ignore exception
			}

			Iterator<EventListener<E>> iterator = listeners.iterator();
			while (iterator.hasNext()) {
				EventListener<E> l = iterator.next();
				try {
					l.handleEvent(e);
				} catch (Exception ee) {
					log.error("error when dispatch event " + e, ee);
				}
			}
		}
	}

	public void dispatchEvent(E e) {
		try {
			eventQueue.put(e);
		} catch (InterruptedException e1) {
			// ignore exception
		}
	}

	public void addListener(EventListener<E> listener) {
		this.listeners.add(listener);
	}

	public void removeListener(EventListener<E> listener) {
		this.listeners.remove(listener);
	}

	public void clearEventListeners() {
		this.listeners.clear();
	}

}
