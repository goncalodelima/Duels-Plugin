/*
 *
 *  * This file is part of Duels-Plugin - https://github.com/goncalodelima/Duels-Plugin
 *  * Copyright (c) 2026 goncalodelima and contributors
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package pt.gongas.duel.util;

import org.bukkit.scheduler.BukkitRunnable;
import pt.gongas.duel.DuelPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ExpiringMap<K, V> {

    private final Map<K, Entry<V>> map = new HashMap<>();
    private final long ttlTicks;
    private final ExpiryHandler<K, V> expiryHandler;

    private static class Entry<V> {

        V value;
        long expireAt;

        Entry(V value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }

    }

    public ExpiringMap(DuelPlugin plugin, int ttlTicks, ExpiryHandler<K, V> expiryHandler) {

        this.ttlTicks = ttlTicks;
        this.expiryHandler = expiryHandler;

        new BukkitRunnable() {
            @Override
            public void run() {
                cleanup();
            }
        }.runTaskTimer(plugin, 20, 20);

    }

    public void put(K key, V value) {
        long ttlMillis = ttlTicks * 50L;
        map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    public V get(K key) {

        Entry<V> entry = map.get(key);

        if (entry == null) {
            return null;
        }

        if (System.currentTimeMillis() > entry.expireAt) {
            map.remove(key);
            return null;
        }

        return entry.value;
    }

    public V remove(K key) {

        Entry<V> entry = map.remove(key);

        if (entry == null || System.currentTimeMillis() > entry.expireAt) {
            return null;
        }

        return entry.value;
    }

    private void cleanup() {

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<K, Entry<V>>> iterator = map.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<K, Entry<V>> entry = iterator.next();

            if (now > entry.getValue().expireAt) {

                K key = entry.getKey();
                V value = entry.getValue().value;

                iterator.remove();

                if (expiryHandler != null) {
                    expiryHandler.onExpire(key, value);
                }

            }

        }

    }

}