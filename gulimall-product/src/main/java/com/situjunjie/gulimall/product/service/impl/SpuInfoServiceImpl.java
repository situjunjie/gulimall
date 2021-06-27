package com.situjunjie.gulimall.product.service.impl;

import com.situjunjie.common.to.SkuReductionTo;
import com.situjunjie.common.to.SpuBoundTo;
import com.situjunjie.common.utils.R;
import com.situjunjie.gulimall.product.entity.*;
import com.situjunjie.gulimall.product.feign.CouponFeignService;
import com.situjunjie.gulimall.product.service.*;
import com.situjunjie.gulimall.product.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.situjunjie.common.utils.PageUtils;
import com.situjunjie.common.utils.Query;

import com.situjunjie.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SpuInfoDescService spuInfoDescService;

    @Autowired
    SpuImagesService spuImagesService;

    @Autowired
    AttrService attrService;

    @Autowired
    ProductAttrValueService productAttrValueService;

    @Autowired
    SkuInfoService skuInfoService;

    @Autowired
    SkuImagesService skuImagesService;

    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    CouponFeignService couponFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    @Transactional
    public void saveSpuInfo(SpuSaveVo spuInfo) {

        //1 保存SpuInfo
        SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(spuInfo,spuInfoEntity);
        spuInfoEntity.setCreateTime(new Date());
        spuInfoEntity.setUpdateTime(new Date());
        this.save(spuInfoEntity);
        log.info("生成的spuId={}",spuInfoEntity.getId());

        //2. 保存Spu描述图片信息
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        List<String> decript = spuInfo.getDecript();
        descEntity.setSpuId(spuInfoEntity.getId());
        descEntity.setDecript(String.join(",",decript));
        log.info("准备插入的descEntity=:{}",descEntity);
        spuInfoDescService.getBaseMapper().insert(descEntity);

        //3.保存Spu 图片集
        List<String> images = spuInfo.getImages();
        spuImagesService.saveImages(spuInfoEntity.getId(),images);
        
        //4.保存spu的基本属性
        List<BaseAttrs> baseAttrs = spuInfo.getBaseAttrs();
        if(baseAttrs!=null && baseAttrs.size()>0){
            List<ProductAttrValueEntity> collect = baseAttrs.stream().map(attr -> {
                ProductAttrValueEntity entity = new ProductAttrValueEntity();
                entity.setAttrId(attr.getAttrId());
                entity.setAttrName(attrService.getById(attr.getAttrId()).getAttrName());
                entity.setAttrValue(attr.getAttrValues());
                entity.setQuickShow(attr.getShowDesc());
                entity.setSpuId(spuInfoEntity.getId());
                return entity;
            }).collect(Collectors.toList());
            productAttrValueService.saveBatch(collect);

        }

        //保存spu的积分属性
        Bounds bounds = spuInfo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds,spuBoundTo);
        spuBoundTo.setSpuId(spuInfoEntity.getId());
        R r1 = couponFeignService.saveSpuBounds(spuBoundTo);
        if(r1.getCode()!=0){
            log.error("调用远程保存会员信息失败");
        }

        //5. 保存sku的基本属性
        List<Skus> skus = spuInfo.getSkus();
        if(skus!=null && skus.size()>0){
            skus.forEach(sku->{
                String defaultimg ="";
                for (Images image : sku.getImages()) {
                    if(image.getDefaultImg()==1){
                        defaultimg = image.getImgUrl();
                        break;
                    }
                }
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(sku,skuInfoEntity);
                skuInfoEntity.setBrandId(spuInfo.getBrandId());
                skuInfoEntity.setSaleCount(0l);
                skuInfoEntity.setSpuId(spuInfoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultimg);
                skuInfoService.save(skuInfoEntity);
                Long skuId = skuInfoEntity.getSkuId();

                //5.2保存所有sku的图片
                List<SkuImagesEntity> collect = sku.getImages().stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(image.getImgUrl());
                    skuImagesEntity.setDefaultImg(image.getDefaultImg());
                    return skuImagesEntity;
                }).filter(item->{
                    return !StringUtils.isEmpty(item.getImgUrl());
                }).collect(Collectors.toList());
                skuImagesService.saveBatch(collect);

                //5.3保存所有sku销售属性
                List<SkuSaleAttrValueEntity> skuAttrs = sku.getAttr().stream().map(attr -> {
                    SkuSaleAttrValueEntity saleAttrValueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(attr, saleAttrValueEntity);
                    saleAttrValueEntity.setSkuId(skuId);
                    return saleAttrValueEntity;
                }).collect(Collectors.toList());
                skuSaleAttrValueService.saveBatch(skuAttrs);

                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties(sku,skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                if (skuReductionTo.getFullCount()>0||skuReductionTo.getFullPrice().compareTo(new BigDecimal("0"))==1){

                R r = couponFeignService.saveSkuReduction(skuReductionTo);
                if(r.getCode()!=0){
                    log.error("调用远程服务保存满减信息失败");
                }
                }


            });
        }


    }

    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        /**
         * status:
         * key:
         * brandId: 0
         * catelogId: 0
         */
        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<SpuInfoEntity>();
        String key = (String) params.get("key");
        String status = (String) params.get("status");
        String brandId = (String) params.get("brandId");
        String catelogId = (String) params.get("catelogId");
        if(!StringUtils.isEmpty(key)){
            wrapper.and(w->{w.eq("id",key).or().like("spu_name",key).or().like("spu_description",key);});
        }
        if(!StringUtils.isEmpty(status)){
            wrapper.eq("publish_status",status);
        }
        if(!StringUtils.isEmpty(brandId)&&!brandId.equals("0")){
            wrapper.eq("brand_id",brandId);
        }
        if(!StringUtils.isEmpty(catelogId)&&!catelogId.equals("0")){
            wrapper.eq("catalog_id",catelogId);
        }


        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

}