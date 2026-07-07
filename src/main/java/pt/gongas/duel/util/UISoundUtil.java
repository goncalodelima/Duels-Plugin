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

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.entity.HumanEntity;

public final class UISoundUtil {

    private UISoundUtil() {}

    public static final Sound ERROR_SOUND = Sound.sound(Key.key("entity.villager.no"), Sound.Source.UI, 1f, 1f);

    public static final Sound SUCCESS_SOUND = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.UI, 1f, 1f);

    public static final Sound UI_SOUND = Sound.sound(Key.key("ui.button.click"), Sound.Source.UI, 1f, 1f);

    public static final Sound BACK_OR_NEXT_SOUND = Sound.sound(Key.key("item.book.page_turn"), Sound.Source.UI, 1f, 1f);

    public static void playErrorSound(HumanEntity humanEntity) {
        humanEntity.playSound(ERROR_SOUND, Sound.Emitter.self());
    }

    public static void playSuccessSound(HumanEntity humanEntity) {
        humanEntity.playSound(SUCCESS_SOUND, Sound.Emitter.self());
    }

    public static void playUISound(HumanEntity humanEntity) {
        humanEntity.playSound(UI_SOUND, Sound.Emitter.self());
    }

    public static void playBackOrNextSound(HumanEntity humanEntity) {
        humanEntity.playSound(BACK_OR_NEXT_SOUND, Sound.Emitter.self());
    }
}
