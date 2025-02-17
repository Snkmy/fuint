package com.fuint.module.backendApi.controller;

import com.fuint.common.Constants;
import com.fuint.common.dto.*;
import com.fuint.common.enums.CouponTypeEnum;
import com.fuint.common.enums.CouponUseForEnum;
import com.fuint.common.enums.StatusEnum;
import com.fuint.common.service.*;
import com.fuint.common.util.PhoneFormatCheckUtils;
import com.fuint.common.util.TokenUtil;
import com.fuint.framework.exception.BusinessCheckException;
import com.fuint.framework.pagination.PaginationRequest;
import com.fuint.framework.pagination.PaginationResponse;
import com.fuint.framework.web.BaseController;
import com.fuint.framework.web.ResponseObject;
import com.fuint.repository.mapper.MtCouponGoodsMapper;
import com.fuint.repository.mapper.MtCouponGroupMapper;
import com.fuint.repository.model.*;
import com.fuint.utils.StringUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 卡券管理类controller
 *
 * Created by FSQ
 * CopyRight https://www.fuint.cn
 */
@Api(tags="管理端-卡券管理相关接口")
@RestController
@AllArgsConstructor
@RequestMapping(value = "/backendApi/coupon")
public class BackendCouponController extends BaseController {

    private MtCouponGroupMapper mtCouponGroupMapper;

    private MtCouponGoodsMapper mtCouponGoodsMapper;

    /**
     * 卡券服务接口
     */
    private CouponService couponService;

    /**
     * 卡券分组服务接口
     * */
    private CouponGroupService couponGroupService;

    /**
     * 店铺服务接口
     */
    private StoreService storeService;

    /**
     * 商品服务接口
     * */
    private GoodsService goodsService;

    /**
     * 卡券发放记录服务接口
     * */
    private SendLogService sendLogService;

    /**
     * 会员服务接口
     * */
    private MemberService memberService;

    /**
     * 短信服务接口
     * */
    private SendSmsService sendSmsService;

    /**
     * 后台账户服务接口
     * */
    private AccountService accountService;

    /**
     * 系统配置服务接口
     * */
    private SettingService settingService;

    /**
     * 查询卡券列表
     *
     * @param  request
     * @return
     * @throws BusinessCheckException
     */
    @ApiOperation(value = "查询卡券列表")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @CrossOrigin
    @PreAuthorize("@pms.hasPermission('coupon:coupon:index')")
    public ResponseObject list(HttpServletRequest request) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        Integer page = request.getParameter("page") == null ? Constants.PAGE_NUMBER : Integer.parseInt(request.getParameter("page"));
        Integer pageSize = request.getParameter("pageSize") == null ? Constants.PAGE_SIZE : Integer.parseInt(request.getParameter("pageSize"));
        Integer groupId = (request.getParameter("groupId") == null || StringUtil.isEmpty(request.getParameter("groupId"))) ? 0 : Integer.parseInt(request.getParameter("groupId"));
        Integer couponId = (request.getParameter("id") == null || StringUtil.isEmpty(request.getParameter("id"))) ? 0 : Integer.parseInt(request.getParameter("id"));
        String name = request.getParameter("name") == null ? "" : request.getParameter("name");
        String type = request.getParameter("type") == null ? "" : request.getParameter("type");
        String status = request.getParameter("status") == null ? "" : request.getParameter("status");

        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }

        PaginationRequest paginationRequest = new PaginationRequest();
        paginationRequest.setCurrentPage(page);
        paginationRequest.setPageSize(pageSize);

        Map<String, Object> params = new HashMap<>();
        if (accountInfo.getMerchantId() != null && accountInfo.getMerchantId() > 0) {
            params.put("merchantId", accountInfo.getMerchantId());
        }
        if (accountInfo.getStoreId() != null && accountInfo.getStoreId() > 0) {
            params.put("storeId", accountInfo.getStoreId());
        }
        if (groupId > 0) {
            params.put("groupId", groupId.toString());
        }
        if (couponId > 0) {
            params.put("id", couponId.toString());
        }
        if (StringUtil.isNotEmpty(name)) {
            params.put("name", name);
        }
        if (StringUtil.isNotEmpty(type)) {
            params.put("type", type);
        }
        if (StringUtil.isNotEmpty(status)) {
            params.put("status", status);
        }
        if (accountInfo.getMerchantId() != null && accountInfo.getMerchantId() > 0) {
            params.put("merchantId", accountInfo.getMerchantId());
        }
        if (accountInfo.getStoreId() != null && accountInfo.getStoreId() > 0) {
            params.put("storeId", accountInfo.getStoreId());
        }

        paginationRequest.setSearchParams(params);
        paginationRequest.setSortColumn(new String[]{"status asc", "createTime desc"});
        PaginationResponse<MtCoupon> paginationResponse = couponService.queryCouponListByPagination(paginationRequest);
        List<MtCoupon> dataList = paginationResponse.getContent();
        List<MtCouponGroup> groupList = new ArrayList<>();

        Map<String, Object> paramsStore = new HashMap<>();
        paramsStore.put("status", StatusEnum.ENABLED.getKey());
        if (accountInfo.getStoreId() != null && accountInfo.getStoreId() > 0) {
            paramsStore.put("storeId", accountInfo.getStoreId().toString());
        }
        if (accountInfo.getMerchantId() != null && accountInfo.getMerchantId() > 0) {
            paramsStore.put("merchantId", accountInfo.getMerchantId());
        }
        List<MtStore> storeList = storeService.queryStoresByParams(paramsStore);

        if (dataList.size() > 0) {
            for (MtCoupon coupon : dataList) {
                MtCouponGroup groupInfo = mtCouponGroupMapper.selectById(coupon.getGroupId());
                if (groupInfo == null) {
                    continue;
                }
                MtCouponGroup g = new MtCouponGroup();
                g.setId(groupInfo.getId());
                g.setName(groupInfo.getName());
                g.setTotal(groupInfo.getTotal());

                Boolean isInGroup = false;
                for (MtCouponGroup gg : groupList) {
                    if (gg.getId().equals(groupInfo.getId())) {
                        isInGroup = true;
                    }
                }
                if (!isInGroup) {
                    groupList.add(g);
                }
            }
        }

        Integer groupTotal = 0;
        if (groupId > 0) {
            MtCouponGroup groupInfo = couponGroupService.queryCouponGroupById(groupId);
            groupTotal = groupInfo.getTotal();
        }

        // 卡券类型列表
        CouponTypeEnum[] typeListEnum = CouponTypeEnum.values();
        List<ParamDto> typeList = new ArrayList<>();
        for (CouponTypeEnum enumItem : typeListEnum) {
            ParamDto paramDto = new ParamDto();
            paramDto.setKey(enumItem.getKey());
            paramDto.setName(enumItem.getValue());
            paramDto.setValue(enumItem.getKey());
            typeList.add(paramDto);
        }

        // 状态列表
        StatusEnum[] statusListEnum = StatusEnum.values();
        List<ParamDto> statusList = new ArrayList<>();
        for (StatusEnum enumItem : statusListEnum) {
            ParamDto paramDto = new ParamDto();
            paramDto.setKey(enumItem.getKey());
            paramDto.setName(enumItem.getValue());
            paramDto.setValue(enumItem.getKey());
            statusList.add(paramDto);
        }

        // 卡券使用专项列表
        CouponUseForEnum[] couponUseForEnum = CouponUseForEnum.values();
        List<ParamDto> couponUseForList = new ArrayList<>();
        for (CouponUseForEnum enumItem : couponUseForEnum) {
            ParamDto paramDto = new ParamDto();
            paramDto.setKey(enumItem.getKey());
            paramDto.setName(enumItem.getValue());
            paramDto.setValue(enumItem.getKey());
            couponUseForList.add(paramDto);
        }

        // 会员等级列表
        Map<String, Object> param = new HashMap<>();
        param.put("status", StatusEnum.ENABLED.getKey());
        List<MtUserGrade> gradeList = memberService.queryMemberGradeByParams(param);

        String imagePath = settingService.getUploadBasePath();

        Map<String, Object> result = new HashMap<>();
        result.put("imagePath", imagePath);
        result.put("groupTotal", groupTotal);
        result.put("storeList", storeList);
        result.put("groupList", groupList);
        result.put("typeList", typeList);
        result.put("statusList", statusList);
        result.put("gradeList", gradeList);
        result.put("couponUseForList", couponUseForList);
        result.put("paginationResponse", paginationResponse);

        return getSuccessResult(result);
    }

    /**
     * 删除卡券
     *
     * @param request
     * @return
     */
    @ApiOperation(value = "删除卡券")
    @RequestMapping(value = "/delete/{id}", method = RequestMethod.GET)
    @CrossOrigin
    @PreAuthorize("@pms.hasPermission('coupon:coupon:index')")
    public ResponseObject delete(HttpServletRequest request, @PathVariable("id") Long id) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }

        List<Long> ids = new ArrayList<>();
        ids.add(id);

        String operator = accountInfo.getAccountName();
        couponService.deleteCoupon(id, operator);

        return getSuccessResult(true);
    }

    /**
     * 保存卡券
     *
     * @param request HttpServletRequest对象
     * @return
     */
    @ApiOperation(value = "保存卡券")
    @RequestMapping(value = "/save", method = RequestMethod.POST)
    @CrossOrigin
    @PreAuthorize("@pms.hasPermission('coupon:coupon:add')")
    public ResponseObject saveCouponHandler(HttpServletRequest request, @RequestBody ReqCouponDto reqCouponDto) throws BusinessCheckException,ParseException {
        String token = request.getHeader("Access-Token");

        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }
        reqCouponDto.setOperator(accountInfo.getAccountName());

        // 同一分组内卡券名称不能重复
        PaginationRequest requestSearch = new PaginationRequest();
        requestSearch.setCurrentPage(Constants.PAGE_NUMBER);
        requestSearch.setPageSize(Constants.PAGE_SIZE);
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("name", reqCouponDto.getName());
        requestParams.put("groupId", reqCouponDto.getGroupId().toString());
        requestSearch.setSearchParams(requestParams);
        PaginationResponse<MtCoupon> dataName = couponService.queryCouponListByPagination(requestSearch);

        if (dataName.getContent().size() > 0 && reqCouponDto.getId() == null) {
            return getFailureResult(201, "卡券名称已存在，请修改");
        }

        TAccount account = accountService.getAccountInfoById(accountInfo.getId());
        if (account.getStoreId() != null && account.getStoreId() > 0) {
            reqCouponDto.setStoreId(account.getStoreId());
        }
        if (account.getMerchantId() != null && account.getMerchantId() > 0) {
            reqCouponDto.setMerchantId(account.getMerchantId());
        }
        couponService.saveCoupon(reqCouponDto);
        return getSuccessResult(true);
    }

    /**
     * 卡券详情
     *
     * @param request
     * @return
     */
    @ApiOperation(value = "卡券详情")
    @RequestMapping(value = "/info/{id}", method = RequestMethod.GET)
    @CrossOrigin
    @PreAuthorize("@pms.hasPermission('coupon:coupon:index')")
    public ResponseObject info(HttpServletRequest request, @PathVariable("id") Integer id) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }

        MtCoupon mtCouponInfo = couponService.queryCouponById(id);

        String baseImage = settingService.getUploadBasePath();

        Map<String, Object> result = new HashMap<>();
        result.put("baseImage", baseImage);
        result.put("couponInfo", mtCouponInfo);

        MtCouponGroup mtGroupInfo = couponGroupService.queryCouponGroupById(mtCouponInfo.getGroupId());
        result.put("groupInfo", mtGroupInfo);

        List<MtStore> storeList = new ArrayList<>();

        if (StringUtil.isNotEmpty(mtCouponInfo.getStoreIds())) {
            String[] ids = mtCouponInfo.getStoreIds().split(",");
            for (String storeId : ids) {
                 MtStore info = storeService.queryStoreById(Integer.parseInt(storeId));
                 storeList.add(info);
            }
        }

        // 卡券适用商品
        List<MtCouponGoods> couponGoodsList = mtCouponGoodsMapper.getCouponGoods(id);
        String goodsIds = "";
        List<MtGoods> goodsList = new ArrayList<>();
        if (couponGoodsList.size() > 0) {
            for (MtCouponGoods cg : couponGoodsList) {
                if (goodsIds.length() > 0) {
                    goodsIds = goodsIds + "," + cg.getGoodsId();
                } else {
                    goodsIds = cg.getGoodsId().toString();
                }
                MtGoods goodsInfo = goodsService.queryGoodsById(cg.getGoodsId());
                goodsList.add(goodsInfo);
            }
        }

        // 不可用日期
        List<DateDto> exceptTimeList = new ArrayList<>();
        if (StringUtil.isNotEmpty(mtCouponInfo.getExceptTime())) {
            String[] exceptTimeArr = mtCouponInfo.getExceptTime().split(",");
            if (exceptTimeArr.length > 0) {
                for (int i = 0; i < exceptTimeArr.length; i++) {
                    if (!exceptTimeArr[i].equals("weekend")) {
                        String[] date = exceptTimeArr[i].split("_");
                        DateDto dto = new DateDto();
                        dto.setStartDate(date[0]);
                        dto.setEndDate(date[1]);
                        exceptTimeList.add(dto);
                    }
                }
            }
        }

        // 储值卡的预存规则
        List<PreStoreRuleDto> preStoreList = new ArrayList<>();
        if (StringUtil.isNotEmpty(mtCouponInfo.getInRule()) && mtCouponInfo.getType().equals(CouponTypeEnum.PRESTORE.getKey())) {
            String[] ruleArr = mtCouponInfo.getInRule().split(",");
            if (ruleArr.length > 0) {
                for (int i = 0; i < ruleArr.length; i++) {
                     if (StringUtil.isNotEmpty(ruleArr[i])) {
                         String[] ruleItem = ruleArr[i].split("_");
                         if (ruleItem.length == 2) {
                             PreStoreRuleDto dto = new PreStoreRuleDto();
                             dto.setPreStoreAmount(ruleItem[0]);
                             dto.setTargetAmount(ruleItem[1]);
                             preStoreList.add(dto);
                         }
                     }
                }
            }
        }

        result.put("exceptTimeList", exceptTimeList);
        result.put("preStoreList", preStoreList);
        result.put("isEdit", true);
        result.put("goodsIds", goodsIds);
        result.put("goodsList", goodsList);
        result.put("storeList", storeList);

        return getSuccessResult(result);
    }

    /**
     * 发放卡券
     *
     * @param request
     * @return
     */
    @ApiOperation(value = "发放卡券")
    @RequestMapping(value = "/sendCoupon", method = RequestMethod.GET)
    @CrossOrigin
    @PreAuthorize("@pms.hasPermission('coupon:coupon:index')")
    public ResponseObject sendCoupon(HttpServletRequest request) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        String mobile = request.getParameter("mobile");
        String num = request.getParameter("num");
        String couponId = request.getParameter("couponId");

        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }

        if (couponId == null) {
            return getFailureResult(201, "系统参数有误");
        }

        if (!PhoneFormatCheckUtils.isChinaPhoneLegal(mobile) && StringUtil.isNotEmpty(mobile)) {
            return getFailureResult(201, "手机号格式有误");
        }

        Pattern pattern = Pattern.compile("[0-9]*");
        if (num == null || (!pattern.matcher(num).matches())) {
            return getFailureResult(201, "发放数量必须为正整数");
        }

        if (Integer.parseInt(num) > 100) {
            return getFailureResult(201, "发放数量最多为100");
        }

        // 导入批次
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        couponService.sendCoupon(Integer.parseInt(couponId), mobile, Integer.parseInt(num), uuid, accountInfo.getAccountName());

        MtCoupon couponInfo = couponService.queryCouponById(Integer.parseInt(couponId));
        MtUser mtUser = memberService.queryMemberByMobile(accountInfo.getMerchantId(), mobile);
        MtCouponGroup mtCouponGroup = couponGroupService.queryCouponGroupById(couponInfo.getGroupId());

        // 发放记录
        ReqSendLogDto dto = new ReqSendLogDto();
        dto.setMerchantId(couponInfo.getMerchantId());
        dto.setType(1);
        dto.setMobile(mobile);
        dto.setUserId(mtUser.getId());
        dto.setFileName("");
        dto.setGroupId(couponInfo.getGroupId());
        dto.setGroupName(mtCouponGroup.getName());
        dto.setCouponId(couponInfo.getId());
        dto.setSendNum(Integer.parseInt(num));
        String operator = accountInfo.getAccountName();
        dto.setOperator(operator);
        dto.setUuid(uuid);
        dto.setMerchantId(accountInfo.getMerchantId());
        dto.setStoreId(accountInfo.getStoreId());
        sendLogService.addSendLog(dto);

        // 发送短信
        try {
            List<String> mobileList = new ArrayList<>();
            mobileList.add(mobile);

            Integer totalNum = 0;
            BigDecimal totalMoney = new BigDecimal("0.0");

            List<MtCoupon> couponList = couponService.queryCouponListByGroupId(couponInfo.getGroupId());
            for (MtCoupon coupon : couponList) {
                totalNum = totalNum + (coupon.getSendNum()*Integer.parseInt(num));
                totalMoney = totalMoney.add((coupon.getAmount().multiply(new BigDecimal(num).multiply(new BigDecimal(coupon.getSendNum())))));
            }

            Map<String, String> params = new HashMap<>();
            params.put("totalNum", totalNum+"");
            params.put("totalMoney", totalMoney+"");
            sendSmsService.sendSms(couponInfo.getMerchantId(), "received-coupon", mobileList, params);
        } catch (Exception e) {
            //empty
        }

        return getSuccessResult(true);
    }
}
