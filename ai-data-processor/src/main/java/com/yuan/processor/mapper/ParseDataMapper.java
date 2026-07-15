package com.yuan.processor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuan.model.entity.ParseData;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ParseDataMapper extends BaseMapper<ParseData> {

    /**
     * 批量插入，单条 SQL 多 VALUES，一次网络往返。
     */
    int insertBatch(List<ParseData> list);
}
