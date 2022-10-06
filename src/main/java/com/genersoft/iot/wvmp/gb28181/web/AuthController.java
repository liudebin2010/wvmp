package com.genersoft.iot.wvmp.gb28181.web;

import com.genersoft.iot.wvmp.service.IUserService;
import com.genersoft.iot.wvmp.storager.dao.dto.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping(value = "/auth")
public class AuthController {

    @Autowired
    private IUserService userService;

    @RequestMapping("/login")
    public String devices(String name, String passwd){
        User user = userService.getUser(name, passwd);
        if (user != null) {
            return "success";
        }else {
            return "fail";
        }
    }
}
