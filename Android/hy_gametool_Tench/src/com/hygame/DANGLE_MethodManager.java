package com.hygame;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.widget.Toast;

import com.downjoy.CallbackListener;
import com.downjoy.CallbackStatus;
import com.downjoy.Downjoy;
import com.downjoy.InitListener;
import com.downjoy.LoginInfo;
import com.downjoy.util.Util;
import com.hy.gametools.manager.HY_Constants;
import com.hy.gametools.manager.HY_ExitCallback;
import com.hy.gametools.manager.HY_GameProxy;
import com.hy.gametools.manager.HY_GameRoleInfo;
import com.hy.gametools.manager.HY_LoginCallBack;
import com.hy.gametools.manager.HY_PayCallBack;
import com.hy.gametools.manager.HY_PayParams;
import com.hy.gametools.manager.HY_SdkResult;
import com.hy.gametools.manager.HY_User;
import com.hy.gametools.manager.HY_UserInfoListener;
import com.hy.gametools.manager.HY_UserInfoParser;
import com.hy.gametools.utils.HY_UserInfoVo;
import com.hy.gametools.manager.HY_UserManagerBase;
import com.hy.gametools.manager.HY_AccountListener;
import com.hy.gametools.manager.HY_Utils;
import com.hy.gametools.utils.CallBackResult;
import com.hy.gametools.utils.Constants;
import com.hy.gametools.utils.DataFromAssets;
import com.hy.gametools.utils.HttpUtils;
import com.hy.gametools.utils.ResultJsonParse;
import com.hy.gametools.utils.JsonGenerator;
import com.hy.gametools.utils.HyLog;
import com.hy.gametools.utils.ProgressUtil;
import com.hy.gametools.utils.ResponseResultVO;
import com.hy.gametools.utils.TransType;
import com.hy.gametools.utils.ToastUtils;
import com.hy.gametools.utils.UrlRequestCallBack;


public class DANGLE_MethodManager extends HY_UserManagerBase implements
        HY_AccountListener, HY_UserInfoListener
{
    private static final String TAG = "HY";
    private static DANGLE_MethodManager instance;
    private Activity mActivity;
    /** isAccessTokenValid:token时效性是否有效 */
    protected static boolean isAccessTokenValid = true;
    /** mIsLandscape:是否是横屏 */
    protected boolean mIsLandscape;
    /** 判断用户是否登出 */
    private boolean isLogout = false;
    /** TokenInfo 保存渠道返回的Token信息 */
    /** 用户信息 */
    private HY_UserInfoVo mChannelUserInfo;
    /** 用户信息的网络获取类 */
    private HY_HttpInfoTask mUserInfoTask;
    /** localXMUser 保存的微讯一键sdk的用户信息,用户回调给CP用户 */
    private HY_User localXMUser;
    /** 保存是否为定额支付 */
    // private boolean mIsFixed;
    private ProgressDialog mProgress;
    private DataFromAssets dataFromAssets;
    /** 支付参数 */
    private HY_PayParams mPayParsms;
    /** 登录回调 */
    private HY_LoginCallBack mLoginCallBack;
    /** 支付回调 */
    private HY_PayCallBack mPayCallBack;
    /** 退出回调 */
    private HY_ExitCallback mExitCallback;
    private Downjoy downjoy;
   

    private DANGLE_MethodManager()
    {
        mChannelUserInfo=new HY_UserInfoVo();
    }

    public static DANGLE_MethodManager getInstance()
    {
        if (instance == null)
        {
            instance = new DANGLE_MethodManager();
        }
        return instance;
    }

    /**
     * clearLoginResult 清除登录信息
     * 
     * @param
     * @return
     */
    private void clearLoginResult()
    {
        this.mChannelUserInfo = null;
    }

    /**
     * applicationInit(初始化一些必须的参数)
     * 
     * @param
     * @return
     */
    @Override
    public void applicationInit(Activity paramActivity,boolean mIsLandscape)
    {
    	this.mIsLandscape = mIsLandscape;
        mActivity = paramActivity;
        HyLog.d(TAG, "MethodManager-->applicationInit");
        
        initChannelDate(paramActivity);
    }

    // ---------------------------------调用渠道SDK接口------------------------------------
    @Override
    public void onCreate(Activity paramActivity)
    {
        mActivity = paramActivity;
       
    }

    /**
     * 初始化的时候获取渠道的一些信息
     */
    private void initChannelDate(Activity paramActivity)
    {
    	String serverSeqNum = "1";
    	dataFromAssets = new DataFromAssets(paramActivity);
    	//assets\hy_game.json 预留接口,设置预留字段
    	dataFromAssets.setmReservedParam1("serverSeqNum");
        try
        {
        	//获取hy_game.sjon 中添加字段的 值
        	serverSeqNum = dataFromAssets.getmReservedParam1();
        
            HyLog.d(TAG, dataFromAssets.toString());
        }
        catch (Exception e)
        {
            HyLog.d(TAG, "初始化参数不能为空");
        }
        
        if(mIsLandscape){
        	//这里是横屏，该渠道没有横竖屏配置,忽略
        	HyLog.d(TAG, "这里是横屏");
        }else{
        	//如果有,就通过这个判断来设置相关
        	HyLog.d(TAG, "这里是竖屏");
        }
        
        //获取AndroidManifest.xml 中 meta-data 中配置的渠道 参数
        String merchantId = HY_Utils.getManifestMeta(paramActivity, "DANGLE_SDK_MERCHANT_ID");
        String appid = HY_Utils.getManifestMeta(paramActivity, "DANGLE_SDK_APP_ID");
        String appkey = HY_Utils.getManifestMeta(paramActivity, "DANGLE_SDK_APP_KEY");
        
        downjoy =Downjoy.getInstance(paramActivity, merchantId, appid,serverSeqNum , appkey, new InitListener() {
			
			@Override
			public void onInitComplete() {
				//渠道要求 sdk初始化完后调用以下两个接口显示浮标
				downjoy.showDownjoyIconAfterLogined(true);
				downjoy.setInitLocation(Downjoy.LOCATION_RIGHT_CENTER_VERTICAL);
				HyLog.d(TAG, "渠道初始化成功");
			}
		});
    }

    @Override
    public void onGotAuthorizationCode(HY_User localXMUser)
    {
        if (null == localXMUser)
        {
            HyLog.i(TAG, "localXMUser:null");
            return;
        }

        // clearLoginResult();
        HyLog.i(TAG, "localXMUser=" + localXMUser);
        // 回调给CP用户的的一些信息
        mLoginCallBack.onLoginSuccess(localXMUser);
    }

    /**
     * 登录接口 
     */
    @Override
    public void doLogin(final Activity paramActivity,final HY_LoginCallBack loginCallBack)
    {
    	
        this.mActivity = paramActivity;
        mLoginCallBack = loginCallBack;
        HyLog.i(TAG, "doLogin-->mIsLandscape=" + mIsLandscape);
       //当乐登录方法
        downjoy.openLoginDialog(paramActivity,
                new CallbackListener<LoginInfo>() {
                    @Override
                    public void callback(int status, LoginInfo data) {
                        if (status == CallbackStatus.SUCCESS
                                && data != null) {
                        	
                        	isLogout = false;//登录状态
                        	
                        	//这里的uid是指我们的uid,服务端生成,目前服务端还没OK，我们测试用渠道的,后续可以删除
                        	
                            mChannelUserInfo.setUserId(data.getUmid());
                            	//正常接入
                            mChannelUserInfo.setChannelUserId(data.getUmid());//渠道uid
                            mChannelUserInfo.setChannelUserName(data.getUserName());//渠道用户名
                            mChannelUserInfo.setToken(data.getToken());//登录验证令牌(token)
                            
                            //进行网络请求                    当前activity          请求模式    目前没有就
//                            onGotTokenInfo(paramActivity, HY_Constants.DO_LOGIN);
                            
                            //测试直接回调给游戏端
                            //true表示登录,false表示切换账号
                            updateUserInfoUi(true);
                            
                        } else if (status == CallbackStatus.FAIL
                                && data != null) {
                        	
                            //回调给游戏，登录失败
                            loginCallBack.onLoginFailed(HY_SdkResult.FAIL,"登录失败:"+ data.getMsg());
                            
                        } else if (status == CallbackStatus.CANCEL
                                && data != null) {
                        	 loginCallBack.onLoginFailed(HY_SdkResult.CANCEL,"登录取消:"+ data.getMsg());
                        }
                    }
                });
    }

    /**
     * 注销接口
     */
    @Override
    public void doLogout(final Activity paramActivity,Object object)
    {
    	//注销登录状态
    	isLogout = true;
        //渠道注销方法
        downjoy.logout(paramActivity);
        //回调给游戏注销成功
        getUserListener().onLogout(HY_SdkResult.SUCCESS, object);
        
    }

    /**
     * 支付接口
     * 
     * @param payParams
     *            支付参数类
     * 
     */
    @Override
    public void doStartPay(Activity paramActivity, HY_PayParams payParams,HY_PayCallBack payCallBack)
    {
        mActivity = paramActivity;
        mPayParsms = payParams;
        mPayCallBack = payCallBack;
        if (isLogout)
        {
            HyLog.d(TAG, "用户已经登出");
//             ToastUtils.show(paramActivity, "用户没有登录，请重新登录后再支付");
            return;
        }
//        if (null != mUserInfoTask)
//        {
            HyLog.d(TAG, ".....请求应用服务器，开始pay支付");
//            mUserInfoTask.startWork(paramActivity, "", this);

            if (null==mChannelUserInfo)
            {
                HyLog.d(TAG, "服务器连接失败。。。  ");
                ToastUtils.show(mActivity, "服务器连接失败。。。");
            }else {
                if (!TextUtils.isEmpty(mChannelUserInfo.getUserId()))
                {
                	//这里是网络请求方法
//                    mUserInfoTask.startWork(paramActivity, HY_Constants.DO_PAY,"", this);
                	//测试跳过网络请求
                	startPayAfter(paramActivity);
                    
                }else {
                    
                    HyLog.d(TAG, "V5账号登录失败。。。  ");
                    ToastUtils.show(mActivity, "V5账号登录失败。。。  ");
                }
            }
//        }
//        else
//        {
//            HyLog.d(TAG, "用户没有登录，请重新登录后再支付");
//            ToastUtils.show(paramActivity, "用户没有登录，请重新登录后再支付");
//            return;
//        }
    	
       
    	
    	
    }

    /**
     * startPayAfter 生成订单后,执行渠道支付方法
     * 
     * @param
     * @return
     */
    private void startPayAfter(final Activity paramActivity)
    {
        HyLog.d(TAG, "调用支付，已经获取到参数。。。。。。。。。");
        int money = mPayParsms.getAmount();//单位:分
        String productName = mPayParsms.getProductName();
        int exchange = mPayParsms.getExchange();
        money = money/100;//因为当乐单位是元,换算成元   ，金额 精度问题可以根据渠道需求来
        productName = money*10 + productName; 
        //游戏订单号
        String gameOrder = mPayParsms.getGameOrderId();
        String body = "测试商品描述";//这里一般是跟支付回调一起回调根据需求填写
        
        downjoy.openPaymentDialog(paramActivity,money, productName, body, gameOrder, DANGLE_RoleInfo.zoneName,DANGLE_RoleInfo.roleName, new CallbackListener<String>() {
			
			@Override
			public void callback(int status, String data) {
				switch (status) {
				case  CallbackStatus.SUCCESS:
					HyLog.e(TAG, "支付成功:"+data);
					//Util.alert(paramActivity, "成功支付回调->订单号：" + data);
					//回调成功
					mPayCallBack.onPayCallback(HY_SdkResult.SUCCESS,"支付成功:"+data);
					break;
				case CallbackStatus.CANCEL:
					HyLog.e(TAG, "支付取消:"+data);
					//Util.alert(paramActivity, "成功取消回调->订单号：" + data);
					mPayCallBack.onPayCallback(HY_SdkResult.CANCEL,"支付取消:"+data);
					break;
				default:
					//Util.alert(paramActivity, "成功失败回调->订单号：" + data);
					HyLog.e(TAG, "支付失败:"+data);
					mPayCallBack.onPayCallback(HY_SdkResult.CANCEL,"支付失败:"+data);
					break;
				}
				
			}
		});
    }

    /**
     *退出接口
     * 
     */
    @Override
    public void doExitQuit(Activity paramActivity,
            HY_ExitCallback paramExitCallback)
    {
        // 如果没有第三方渠道的接口，则直接回调给用户，让用户自己定义自己的退出界面
        // paramExitCallback.onNo3rdExiterProvide();

        mActivity = paramActivity;
        mExitCallback = paramExitCallback;
        mExitCallback.onGameExit();
        HyLog.d(TAG, "已经执行doExitQuit。。。。");
    }

    /**
     * 应用服务器通过此方法返回UserInfo
     * 
     * @param userInfo
     */
    public void onGotUserInfo(HY_UserInfoVo userInfo)
    {
        ProgressUtil.dismiss(mProgress);

        mChannelUserInfo = userInfo;

        if (userInfo == null)
        {
            HyLog.d(TAG, "未获取到渠道 UserInfo");
        }
        else
        {
            if (!userInfo.isValid())
            {
                if (TextUtils.isEmpty(userInfo.getErrorMessage()))
                {
                    HyLog.d(TAG, "未获取到渠道     UserInfo");
                }
                else
                {
                    HyLog.d(TAG, "getError:" + userInfo.getErrorMessage());
                }
            }
        }
        updateUserInfoUi(true);
    }

    private void updateUserInfoUi(boolean isLogin)
    {
        HyLog.d(TAG, "updateUserInfoUi.....");
        if (mChannelUserInfo != null && mChannelUserInfo.isValid())
        {
            localXMUser = new HY_User(mChannelUserInfo.getUserId(),
                    mChannelUserInfo.getChannelUserId(),
                    mChannelUserInfo.getChannelUserName(),
                    mChannelUserInfo.getToken());
            mLoginCallBack.onLoginSuccess(localXMUser);
        }

    }

    /**
     * onGotTokenInfo(首先需要保存AccessToken，然后需要用AccessToken换取UserInfo)
     * 
     * @param tokenInfo
     *            用户的token信息
     * @return
     */
    public void onGotTokenInfo(Activity paramActivity,int state)
    {
        mActivity = paramActivity;

            mUserInfoTask = new HY_HttpInfoTask();

            // 提示用户进度
            mProgress = ProgressUtil.showByString(mActivity,
                    "登录验证信息", "正在请求V5应用服务器，请稍候……",
                    new DialogInterface.OnCancelListener()
                    {

                        @Override
                        public void onCancel(DialogInterface dialog)
                        {
                            if (mUserInfoTask != null)
                            {
                                mUserInfoTask = null;
                            }
                        }
                    });

            if (null != mUserInfoTask)
            {
                HyLog.d(TAG, ".....请求应用服务器，用AccessToken换取UserInfo");
                // ToastUtils.show(mActivity,
                // ".....请求应用服务器，用AccessToken换取UserInfo");
                // 请求应用服务器，用AccessToken换取UserInfo
                mUserInfoTask.startWork(paramActivity,
                		state , mChannelUserInfo.getToken(), this);
            }
        }
      

    // ----------------------------------------------------------------


    @Override
    public void onGotError(int paramInt)
    {
        HyLog.d(TAG, "onGotError,..... ");

        clearLoginResult();
    }

  

    /**
     * HY_HttpInfoTask 类描述： 用户信息的网络获取类 创建人：smile
     */
    class HY_HttpInfoTask implements UrlRequestCallBack
    {

        private static final String TAG = "HY";
        private HttpUtils mHttpUtils;
        public boolean isRunning = false;
        private Activity mContext;
        private String access_token;

        private JSONObject paramsJson;
        private HY_UserInfoListener userInfo_listener;

        public HY_HttpInfoTask()
        {
            super();
            mHttpUtils = new HttpUtils();

        }

        /**
         * startWork(启动网络请求)
         * 
         * @param accessToken
         *            传值非空为登录，传值空字符串""为支付
         * @return
         */
        public void startWork(Activity mActivity,
        		int state , String accessToken,
                final HY_UserInfoListener listener)
        {

            if (!isRunning)
            {
                this.mContext = mActivity;
                this.access_token = accessToken;

                userInfo_listener = listener;
                // 获取应用服务器的demo
                // String postUrl = Constants.URL_GET_TOKEN;
                ResultJsonParse channel_parser = new HY_UserInfoParser();
                JsonGenerator jsonObject;
                try
                {
                	 if(HY_Constants.DO_LOGIN == state || HY_Constants.SWITCH_ACCOUNT == state){
                     	jsonObject = HttpUtils.getLoginInfoRequest(mChannelUserInfo);
                         paramsJson = new JSONObject(jsonObject.toString());
                     	mHttpUtils.doPost(mContext, Constants.URL_LOGIN,
                                 paramsJson, this, channel_parser);
                     }else{
                     	
                     	jsonObject = HttpUtils.getPayInfoRequest(mPayParsms,
                                 mChannelUserInfo);
                     	  paramsJson = new JSONObject(jsonObject.toString());
                       	mHttpUtils.doPost(mContext, Constants.URL_PAY,
                                   paramsJson, this, channel_parser);
                     }
                }
                catch (JSONException e)
                {
                    HyLog.e(TAG,
                            "HY_HttpInfoTask-->JSONException:"
                                    + e.toString());
                }
            }
            else
            {
                HyLog.e("TAG", "登录重新获取工作正在进行");
            }
        }

        @Override
        public void urlRequestStart(CallBackResult result)
        {
            isRunning = true;
        }

        @Override
        public void urlRequestEnd(CallBackResult result)
        {
            isRunning = false;
            if (null != mProgress)
            {

                ProgressUtil.dismiss(mProgress);
                mProgress = null;
            }

            try
            {
                if (null != result && result.obj != null)
                {
                    ResponseResultVO resultVO = (ResponseResultVO) result.obj;
                    // 通过返回的渠道类型，判断是调用的支付接口还是登录接口
                    if (resultVO.transType.equals(TransType.CREATE_USER
                            .toString()))
                    {
                        // 登录接口
                        if (resultVO.responseCode.equals("0000"))
                        {
                            if (resultVO.obj != null
                                    && resultVO.obj instanceof HY_UserInfoVo)
                            {
                                HyLog.d(TAG, "登录接口返回success：" + resultVO.message);

                                mChannelUserInfo = (HY_UserInfoVo) resultVO.obj;
                                userInfo_listener.onGotUserInfo(mChannelUserInfo);
                            }
                        }
                        else
                        {
                            HyLog.d(TAG, "登录接口返回fail：" + resultVO.message);
                            if (null != mProgress)
                            {

                                ProgressUtil.dismiss(mProgress);
                                mProgress = null;
                            }
                            mLoginCallBack.onLoginFailed(HY_SdkResult.FAIL,
                            		"登录失败:"+Integer.parseInt(resultVO.responseCode)+ resultVO.message);
                            
                        }

                    }
                    else if (resultVO.transType.equals(TransType.CREATE_ORDER
                            .toString()))
                    {
                        // 支付接口
                        if (resultVO.responseCode.equals("0000"))
                        {
                            if (resultVO.obj != null
                                    && resultVO.obj instanceof HY_UserInfoVo)
                            {
                                HyLog.d(TAG, "支付接口返回success：" + resultVO.message);
                                HY_UserInfoVo temp_userInfoVo = (HY_UserInfoVo) resultVO.obj;
                                HyLog.d(TAG,
                                        "订单号："
                                                + temp_userInfoVo
                                                        .getMyOrderId()
                                                + "游戏订单号："
                                                + temp_userInfoVo
                                                        .getGameOrderId()
                                                + "渠道账号："
                                                + mChannelUserInfo
                                                        .getChannelUserId());
                                if (TextUtils.isEmpty(temp_userInfoVo
                                        .getMyOrderId()))
                                {
                                    HyLog.d(TAG,
                                            "创建订单成功后-->服务端返回的游戏订单MyOrderId不成功订单成功，可能为null");
                                }
                                if (TextUtils.isEmpty(temp_userInfoVo
                                        .getGameOrderId()))
                                {
                                    HyLog.d(TAG,
                                            "创建订单成功后-->服务端返回的游戏订单GameOrderId不成功订单成功，可能为null");
                                }
                                mChannelUserInfo.setMyOrderId(temp_userInfoVo
                                        .getMyOrderId());// 传入应用订单号
                                mChannelUserInfo.setGameOrderId(temp_userInfoVo
                                        .getGameOrderId());// 游戏订单
                                HyLog.d(TAG, "创建订单成功后-->mChannelUserInfo："
                                        + mChannelUserInfo.toString());

                                startPayAfter(mContext);
                            }
                        }
                        else
                        {
                            HyLog.d(TAG, "支付接口返回fail：" + resultVO.message);
                            if (null != mProgress)
                            {

                                ProgressUtil.dismiss(mProgress);
                                mProgress = null;
                            }
                            mPayCallBack.onPayCallback(HY_SdkResult.FAIL,
                                    resultVO.message);
                        }

                    }
                    else
                    {
                        HyLog.d(TAG, "接口传输不对，既不是登录也不是支付：" + resultVO.message);
                    }
                }
            }
            catch (Exception e)
            {
                HyLog.e(TAG, "网络异常，请稍后再试" + e.toString());
                mLoginCallBack.onLoginFailed(HY_SdkResult.FAIL,"网络异常，请稍后再试:");
                ToastUtils.show(mActivity, "网络异常，请稍后再试");
            }
        }

        @Override
        public void urlRequestException(CallBackResult result)
        {
            isRunning = false;
            HyLog.e(TAG, "urlRequestException" + result.url + "," + result.param
                    + "," + result.backString);
            ToastUtils.show(mActivity, "网络异常，请稍后再试");

            mLoginCallBack.onLoginFailed(HY_SdkResult.FAIL,"网络异常,请稍后再试:");
           
            if (null != mProgress)
            {

                ProgressUtil.dismiss(mProgress);
                mProgress = null;
            }
        }

    }

    @Override
    public void onStop(Activity paramActivity)
    {
        HyLog.d(TAG, "MethodManager-->onStop");

    }

    @Override
    public void onResume(Activity paramActivity)
    {
        HyLog.d(TAG, "MethodManager-->onStop");
        //渠道生命周期要求
        if (downjoy != null) {
            downjoy.resume(paramActivity);
        }

    }

    @Override
    public void onPause(Activity paramActivity)
    {
        HyLog.d(TAG, "MethodManager-->onPause");
      //渠道生命周期要求
        if (downjoy != null) {
            downjoy.pause();
        }

    }

    @Override
    public void onRestart(Activity paramActivity)
    {
        HyLog.d(TAG, "MethodManager-->onRestart");

    }

    @Override
    public void applicationDestroy(Activity paramActivity)
    {
        HyLog.d(TAG, "MethodManager-->applicationDestroy");

    }
    @Override
    public void onDestroy(Activity paramActivity)
    {
        HyLog.d(TAG, "MethodManager-->onDestroy");
      //渠道生命周期要求
        if (downjoy != null) {
            downjoy.destroy();
            downjoy = null;
        }

    }

    @Override
    public void setRoleData(Activity paramActivity, HY_GameRoleInfo gameRoleInfo)
    {
    	//提供了两个接口
    	//1、游戏上传，我们在这里传给 渠道
    	//2、保存最后一次上传信息，根据渠道需求进行上传信息
    	//以下参数是游戏传入角色信息,如果渠道需要,就根据上传类型判断，传输数据
    	
    	DANGLE_RoleInfo.typeId = gameRoleInfo.getTypeId();//上传类型 登录:HY_Constants.ENTER_SERVER 、创角:HY_Constants.CREATE_ROLE、升级:HY_Constants。LEVEL_UP
    	DANGLE_RoleInfo.roleId = gameRoleInfo.getRoleId();//角色id
    	DANGLE_RoleInfo.roleName = gameRoleInfo.getRoleName();//角色名
    	DANGLE_RoleInfo.roleLevel = gameRoleInfo.getRoleLevel();//角色等级
    	DANGLE_RoleInfo.zoneId = gameRoleInfo.getZoneId();//区服id
    	DANGLE_RoleInfo.zoneName = gameRoleInfo.getZoneName();//区服名
    	DANGLE_RoleInfo.balance = gameRoleInfo.getBalance(); //用户余额
    	DANGLE_RoleInfo.vip = gameRoleInfo.getVip();//vip等级
    	DANGLE_RoleInfo.partyName = gameRoleInfo.getPartyName();//帮派名称
    	//这里是为了显示例子,正式的时候就不要弹Toast了
    	Toast.makeText(paramActivity, gameRoleInfo.toString(), Toast.LENGTH_SHORT).show();
        HyLog.d(TAG, "MethodManager-->setExtData");
    }



}