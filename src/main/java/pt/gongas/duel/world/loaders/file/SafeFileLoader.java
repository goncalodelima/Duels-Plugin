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

package pt.gongas.duel.world.loaders.file;

import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SafeFileLoader implements SlimeLoader {

    private static final FilenameFilter WORLD_FILE_FILTER = (dir, name) -> name.endsWith(".slime");
    private static final Logger LOGGER = LoggerFactory.getLogger(SafeFileLoader.class);

    private final File worldDir;

    public SafeFileLoader(File worldDir) throws IllegalStateException {
        this.worldDir = worldDir;

        if (worldDir.exists() && !worldDir.isDirectory()) {
            LOGGER.warn("A file named '{}' has been deleted, as this is the name used for the worlds directory.", worldDir.getName());
            if (!worldDir.delete()) throw new IllegalStateException("Failed to delete the file named '" + worldDir.getName() + "'.");
        }

        if (!worldDir.exists() && !worldDir.mkdirs()) throw new IllegalStateException("Failed to create the worlds directory.");
    }

    @Override
    public byte[] readWorld(String worldName) throws UnknownWorldException, IOException {
        if (!worldExists(worldName)) {
            throw new UnknownWorldException(worldName);
        }

        try (FileInputStream fis = new FileInputStream(new File(worldDir, worldName + ".slime"))){
            return fis.readAllBytes();
        }
    }

    @Override
    public boolean worldExists(String worldName) {
        return new File(worldDir, worldName + ".slime").exists();
    }

    @Override
    public List<String> listWorlds() throws NotDirectoryException {
        String[] worlds = worldDir.list(WORLD_FILE_FILTER);

        if (worlds == null) {
            throw new NotDirectoryException(worldDir.getPath());
        }

        return Arrays.stream(worlds).map((c) -> c.substring(0, c.length() - 6)).collect(Collectors.toList());
    }

//    @Override
//    public void saveWorld(String worldName, byte[] serializedWorld) throws IOException {
//        try (FileOutputStream fos = new FileOutputStream(new File(worldDir, worldName + ".slime"))) {
//            fos.write(serializedWorld);
//        }
//    }

    @Override
    public void saveWorld(String worldName, byte[] serializedWorld) throws IOException {
        Path target = new File(worldDir, worldName + ".slime").toPath();
        Path temp = new File(worldDir, worldName + ".slime.tmp").toPath();

        Files.write(temp, serializedWorld, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        Files.move(
                temp,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    @Override
    public void deleteWorld(String worldName) throws UnknownWorldException, IOException {
        if (!worldExists(worldName)) {
            throw new UnknownWorldException(worldName);
        } else {
            if (!new File(worldDir, worldName + ".slime").delete()) {
                throw new IOException("Failed to delete the world file. File#delete() returned false.");
            }
        }
    }

}
