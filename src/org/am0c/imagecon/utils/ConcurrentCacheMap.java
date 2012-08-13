package org.am0c.imagecon.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


public class ConcurrentCacheMap<K, V> extends ConcurrentHashMap<K, V> {

	/**
	 * serial version uid 
	 */
	private static final long serialVersionUID = -8863204112774701057L;
	
	private final ReentrantLock lock = new ReentrantLock();
	private int capacity;
	
	private ArrayList<K> keys;
	
	public ConcurrentCacheMap(int capacity) {
		super();
		this.capacity = capacity;
		keys = new ArrayList<K>();
	}
	
	@Override
	public void clear() {
		lock.lock();
		try {
			super.clear();
			keys.clear();
		} finally {
			lock.unlock();
		}
	}
	
	@Override
	public V put(K key, V value) {
		V v;
		lock.lock();
		try {
			v = super.put(key, value);
			keys.add(key);
			if (keys.size() > capacity)
				keys.remove(0);
		} finally {		
			lock.unlock();
		}
		return v;
	}
	
	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		lock.lock();
		try {
			super.putAll(m);
			Iterator<? extends
				Map.Entry<? extends K, ? extends V>> it = m.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<? extends K, ? extends V> entry = it.next();
				this.put(entry.getKey(), entry.getValue());
			}
		} finally {
			lock.unlock();
		}
	}
	
	@Override
	public V putIfAbsent(K key, V value) {
		V v;
		lock.lock();
		try {
			v = super.putIfAbsent(key, value);
			if (v != null) {
				keys.add(key);
				if (keys.size() > capacity)
					keys.remove(0);
			}
		} finally {
			lock.unlock();
		}
		return v;
	}

	@Override
	public V remove(Object key) {
		V v;
		lock.lock();
		try {
			v = super.remove(key);
			if (v != null) {
				keys.remove(key);
			}
		} finally {
			lock.unlock();
		}
		return v;
	}
	
	@Override
	public boolean remove(Object key, Object value) {
		boolean is_success;
		lock.lock();
		try {
			is_success = super.remove(key, value);
			if (is_success) {
				keys.remove(key);
			}
		} finally {
			lock.unlock();
		}
		return is_success; 
	}
}
