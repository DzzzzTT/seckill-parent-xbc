package com.bmw.seckill.security;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.bmw.seckill.common.base.BaseResponse;
import com.bmw.seckill.common.entity.CommonWebUser;
import com.bmw.seckill.common.exception.ErrorMessage;
import com.bmw.seckill.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 验证用户是否已经登录，需要验证的接口必须登录.
 */
@Slf4j
@WebFilter(filterName="userLoginFilter", urlPatterns = "/*")
public class UserLoginFilter implements Filter {

    @Autowired
    private RedisUtil redisUtil;

    //本filter配置的是拦截所有，urlPattern是配置的需要拦截的地址，其他地址不做拦截
    @Value("${auth.login.pattern}")
    private String urlPattern;

    @Override
    public void doFilter(ServletRequest servletRequest,
                         ServletResponse servletResponse,
                         FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest)servletRequest;
        HttpServletResponse response = (HttpServletResponse)servletResponse;
        HttpSession session = request.getSession();

//        getRequestURL方法返回客户端发出请求时的完整URL。   URL即完整请求
//        getRequestURI方法返回请求行中的资源名部分  URI即端口后面的所有内容
        String url = request.getRequestURI();
        log.info("url:=" +url+ ",pattrn:="+urlPattern);
        //matches方法判断此字符串是否与给定的正则表达式匹配。
        if (url.matches(urlPattern)) {
            if (session.getAttribute(WebUserUtil.SESSION_WEBUSER_KEY) != null) {
                filterChain.doFilter(request, response);
                return;
            } else {
                //token我们此处约定保存在http协议的header中，也可以保存在cookie中，
                // 调用我们接口的前端或客户端也会保存cookie，具体使用方式由公司确定
                String tokenValue = request.getHeader("token");
                if (StringUtils.isNotEmpty(tokenValue)) {
                    Object object = redisUtil.get(tokenValue);
                    if (object != null) {
                        CommonWebUser commonWebUser = JSONObject.parseObject(object.toString(), CommonWebUser.class);
                        session.setAttribute(WebUserUtil.SESSION_WEBUSER_KEY, commonWebUser);

                        filterChain.doFilter(request, response);
                        return;
                    } else {
                        //返回接口调用方需要登录的错误码，接口调用方开始登录
                        returnJson(response);
                        return;
                    }
                } else {
                    //返回接口调用方需要登录的错误码，接口调用方开始登录
                    returnJson(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
        return;
    }

    /**
     * 返回需要登录的约定格式的错误码，接口调用方根据错误码进行登录操作.
     */
    private void returnJson(ServletResponse response) {
        PrintWriter writer = null;
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=utf-8");
        try {
            writer = response.getWriter();
            BaseResponse baseResponse = new BaseResponse(ErrorMessage.USER_NEED_LOGIN.getCode(),
                    ErrorMessage.USER_NEED_LOGIN.getMessage(), null);
            writer.print(JSON.toJSONString(baseResponse));
        } catch (IOException e) {
            log.error("response error", e);
        } finally {
            if (writer != null)
                writer.close();
        }
    }

}
