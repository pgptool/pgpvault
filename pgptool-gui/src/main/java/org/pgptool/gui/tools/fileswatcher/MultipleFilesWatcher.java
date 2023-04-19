/*******************************************************************************
 * PGPTool is a desktop application for pgp encryption/decryption
 * Copyright (C) 2023 Sergey Karpushin
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package org.pgptool.gui.tools.fileswatcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.File;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

/**
 * @author Fredrick R. Brennan <copypaste@kittens.ph>
 */
public class MultipleFilesWatcher {
	private static Logger log = Logger.getLogger(MultipleFilesWatcher.class);

	private FilesWatcherHandler dirWatcherHandler;
	private String watcherName;
	private FileAlterationMonitor monitor;
	private Map<String, FileAlterationObserver> observers = new HashMap<>();

	public MultipleFilesWatcher(FilesWatcherHandler dirWatcherHandler, String watcherName) {
		this.dirWatcherHandler = dirWatcherHandler;
		this.watcherName = watcherName;
		this.monitor = new FileAlterationMonitor(1000);
		startWatcher();
	}

	private void startWatcher() {
		try {
			monitor.start();
		} catch (Exception e) {
			throw new RuntimeException("Failed to start the FileAlterationMonitor", e);
		}
	}

	public void watchForFileChanges(String filePathName) {
		String baseFolderStr = FilenameUtils.getFullPathNoEndSeparator(filePathName);

		synchronized (watcherName) {
			if (observers.containsKey(baseFolderStr)) {
				return;
			}

			FileAlterationObserver observer = new FileAlterationObserver(baseFolderStr);
			observer.addListener(new FileAlterationListenerAdaptor() {
				@Override
				public void onFileChange(File file) {
					dirWatcherHandler.handleFileChanged(ENTRY_MODIFY, file.getAbsolutePath());
				}

				@Override
				public void onFileCreate(File file) {
					dirWatcherHandler.handleFileChanged(ENTRY_CREATE, file.getAbsolutePath());
				}

				@Override
				public void onFileDelete(File file) {
					dirWatcherHandler.handleFileChanged(ENTRY_DELETE, file.getAbsolutePath());
				}
			});

			monitor.addObserver(observer);
			observers.put(baseFolderStr, observer);
		}
	}

	public void stopWatchingFile(String filePathName) {
		String baseFolderStr = FilenameUtils.getFullPathNoEndSeparator(filePathName);

		synchronized (watcherName) {
			FileAlterationObserver observer = observers.get(baseFolderStr);
			if (observer == null) {
				return;
			}

			monitor.removeObserver(observer);
			observers.remove(baseFolderStr);
		}
	}

	public void stopWatcher() {
		try {
			monitor.stop();
		} catch (Exception e) {
			log.error("Failed to gracefully close FileAlterationMonitor", e);
		}
	}
}
