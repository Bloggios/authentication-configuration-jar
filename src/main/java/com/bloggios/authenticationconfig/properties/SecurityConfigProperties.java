/*
 * Copyright © 2023-2024 Rohit Parihar and Bloggios
 * All rights reserved.
 * This software is the property of Rohit Parihar and is protected by copyright law.
 * The software, including its source code, documentation, and associated files, may not be used, copied, modified, distributed, or sublicensed without the express written consent of Rohit Parihar.
 * For licensing and usage inquiries, please contact Rohit Parihar at rohitparih@gmail.com, or you can also contact support@bloggios.com.
 * This software is provided as-is, and no warranties or guarantees are made regarding its fitness for any particular purpose or compatibility with any specific technology.
 * For license information and terms of use, please refer to the accompanying LICENSE file or visit http://www.apache.org/licenses/LICENSE-2.0.
 * Unauthorized use of this software may result in legal action and liability for damages.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bloggios.authenticationconfig.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Owner - Rohit Parihar
 * Author - rohit
 * Project - bloggios-config-auth-provider
 * Package - com.bloggios.authenticationconfig.properties
 * Created_on - 14 December-2023
 * Created_at - 19 : 02
 */

@ConfigurationProperties(prefix = "security-config")
@Configuration
@Getter
@Setter
public class SecurityConfigProperties {

    private Exclude exclude;
    private KeyProvider keyProvider = new KeyProvider();
    private Cookie cookie;

    @Getter
    @Setter
    public static class Exclude {
        private List<String> paths;
    }

    @Getter
    @Setter
    public static class KeyProvider {
        private RSAPublicKey publicKey;
    }

    @Getter
    @Setter
    public static class Cookie {
        private String refreshCookieName;
        private String cookieName;
        private List<String> paths = new ArrayList<>();
    }
}
