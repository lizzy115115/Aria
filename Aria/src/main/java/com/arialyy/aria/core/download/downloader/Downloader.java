/*
 * Copyright (C) 2016 AriaLyy(https://github.com/AriaLyy/Aria)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arialyy.aria.core.download.downloader;

import com.arialyy.aria.core.AriaManager;
import com.arialyy.aria.core.common.AbsFileer;
import com.arialyy.aria.core.common.AbsThreadTask;
import com.arialyy.aria.core.common.SubThreadConfig;
import com.arialyy.aria.core.download.DownloadEntity;
import com.arialyy.aria.core.download.DownloadTaskEntity;
import com.arialyy.aria.core.inf.AbsTaskEntity;
import com.arialyy.aria.core.inf.IDownloadListener;
import com.arialyy.aria.util.ALog;
import com.arialyy.aria.util.BufferedRandomAccessFile;
import com.arialyy.aria.util.CommonUtil;
import com.arialyy.aria.util.ErrorHelp;
import java.io.File;
import java.io.IOException;

/**
 * Created by AriaL on 2017/7/1.
 * 文件下载器
 */
class Downloader extends AbsFileer<DownloadEntity, DownloadTaskEntity> {
  private String TAG = "Downloader";

  Downloader(IDownloadListener listener, DownloadTaskEntity taskEntity) {
    super(listener, taskEntity);
    mTempFile = new File(mEntity.getDownloadPath());
    AriaManager manager = AriaManager.getInstance(AriaManager.APP);
    setUpdateInterval(manager.getDownloadConfig().getUpdateInterval());
  }

  @Override protected int setNewTaskThreadNum() {
    return
        // 小于1m的文件或是任务组的子任务、使用虚拟文件，线程数都是1
        mEntity.getFileSize() <= SUB_LEN
            || mTaskEntity.getRequestType() == AbsTaskEntity.D_FTP_DIR
            || mTaskEntity.getRequestType() == AbsTaskEntity.DG_HTTP
            || mRecord.isOpenDynamicFile
            ? 1
            : AriaManager.getInstance(mContext).getDownloadConfig().getThreadNum();
  }

  @Override protected boolean handleNewTask() {
    CommonUtil.createFile(mTempFile.getPath());
    BufferedRandomAccessFile file = null;
    try {
      file = new BufferedRandomAccessFile(new File(mTempFile.getPath()), "rwd", 8192);
      //设置文件长度
      file.setLength(mRecord.isOpenDynamicFile ? 1 : mEntity.getFileSize());
      return true;
    } catch (IOException e) {
      failDownload("下载失败【downloadUrl:"
          + mEntity.getUrl()
          + "】\n【filePath:"
          + mEntity.getDownloadPath()
          + "】\n"
          + ALog.getExceptionString(e));
    } finally {
      if (file != null) {
        try {
          file.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return false;
  }

  @Override protected AbsThreadTask selectThreadTask(SubThreadConfig<DownloadTaskEntity> config) {
    switch (mTaskEntity.getRequestType()) {
      case AbsTaskEntity.D_FTP:
      case AbsTaskEntity.D_FTP_DIR:
        return new FtpThreadTask(mConstance, (IDownloadListener) mListener, config);
      case AbsTaskEntity.D_HTTP:
        return new HttpThreadTask(mConstance, (IDownloadListener) mListener, config);
    }
    return null;
  }

  private void failDownload(String errorMsg) {
    closeTimer();
    ALog.e(TAG, errorMsg);
    mConstance.isRunning = false;
    mListener.onFail(false);
    ErrorHelp.saveError(TAG, "", errorMsg);
  }
}
