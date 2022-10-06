package com.genersoft.iot.wvmp.service;

import com.genersoft.iot.wvmp.storager.dao.dto.RecordInfo;
import com.github.pagehelper.PageInfo;

public interface IRecordInfoServer {
    PageInfo<RecordInfo> getRecordList(int page, int count);
}
