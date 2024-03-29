package com.situjunjie.gulimall.ware.dao;

import com.situjunjie.gulimall.ware.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品库存
 * 
 * @author situjunjie
 * @email situjunjie@vip.qq.com
 * @date 2021-06-16 13:33:09
 */
@Mapper
public interface WareSkuDao extends BaseMapper<WareSkuEntity> {

    Long selectSkuStock(@Param("skuId") Long skuid);

    Long lockSkuStock(@Param("skuId") Long skuId, @Param("count") Integer count);

    void unLockOrderStock(@Param("skuId") Long skuId, @Param("wareId") Long wareId, @Param("count") Integer count);
}
