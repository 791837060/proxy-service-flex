package com.gwb.proxyservice.servlet;

import lombok.Data;

/**
 * @ClassName: LoginDto
 * @Description: java类作用描述
 * @Author: zhk
 * @CreateDate: 2020/5/28 17:21
 * @Version: 1.0
 */
@Data
public class LoginDto {

    String country;
    String password;
    String mobilephone;
}
