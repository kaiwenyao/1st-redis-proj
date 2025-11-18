### Intro

这个项目实现了使用redis进行短信验证以及用户信息存取。

核心文件：

1. 两个拦截器和配置文件：

- com.hmdp.utils.RefreshTokenInterceptor
- com.hmdp.utils.LoginInterceptor
- com.hmdp.config.MvcConfig

2. 用户服务实现：

- com.hmdp.service.impl.UserServiceImpl

### 短信验证思路

分为发送发送和接收。

#### **发送**：

生成验证码，存储到redis中。key为手机号。

#### 接收：

用手机号去redis中取数据，然后比较是否一致。若一致，则可以成功注册/登录。

在mysql数据库中查找用户是否存在，若已存在，则为登录；若不存在，则为注册。

### 用户信息存取：

登录后，生成一个随机的token，把UserDTO存储进去（用户的账号头像，无敏感信息）。

返回token到前端。

每次请求都携带这个token，后端去redis中寻找token，若存在token，则取出UserDTO放入ThreadLocal，以便请求中调用。









