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

# 短信验证登录

基于session实现 VS 基于redis实现

### 基于session实现

#### 发送短信验证码

生成验证码，保存到session中。`session.setAttribute`保存到后端服务器中。

session的原理：

session依赖cookie。

后端返回的时候，会自动夹带一个 Header： `Set-Cookie: JSESSIONID=AE123456789; Path=/; HttpOnly`

浏览器收到响应，会知道需要设置cookie，会把登录凭证`JSESSIONID`存储到浏览器的cookie区。

后续的请求在发送时，会自动在请求头上面加上cookie，也就是一个session的id，然后到后端服务器中搜索这个已经创建出来的session。

session 的弊端：session存储在后端服务器的内存中，当后端服务器重启时，会导致丢失；多台服务器不共享

<img src="https://lsky.ikun.uk/a/2025/12/17/a7R4Z1LJrS.webp" alt="image-20251217下午13602269" style="zoom: 50%;" />

当收到请求时，要做拦截器验证：

从请求中获取session id，然后获取服务器中的session中的用户：如果存在，则成功；不存在，则拦截。

### 基于redis实现

把生成的短信验证码存储到redis中。key根据用户的手机号生成。

登录后，需要存储**登录凭证**。登录凭证以哈希的数据结构（键值对）存储到redis中。

键值对中的键是随机生成的token；值是用户DTO。如图：

![image-20251217下午15022682](https://lsky.ikun.uk/a/2025/12/17/djO6BbSgtX.webp)

#### 两个拦截器

第一个拦截器负责刷新token有效期，第二个拦截器负责做登录校验。

**`WebMvcConfigurer` 接口：** 这是 Spring MVC 提供的一张“配置表”。它里面有很多空方法（default methods），比如“配置拦截器”、“配置跨域”、“配置资源映射”等。

**`addInterceptors` 方法：** 这是专门用来注册“保安”（拦截器）的地方。

第一步：声明这是一个配置类

```java
@Configuration // 告诉 Spring：这是一个配置类，启动时要加载我
public class MvcConfig implements WebMvcConfigurer { ... }
```

第二步：注入依赖

```java
@Resource
private StringRedisTemplate stringRedisTemplate;
```

因为 `RefreshTokenInterceptor` 需要用到 Redis（去查 Token 还在不在），但拦截器本身通常是我们手动 `new` 出来的，Spring 没法直接把 Redis 注入给拦截器。 **做法：** 先把 Redis 工具注入给配置类（MvcConfig），再由配置类像传家宝一样传给拦截器。

***为什么一定要注入`StringRedisTemplate`？为什么不能手动new？***

因为手动new出来的对象，不会读取 `application.yml`配置文件中的配置去连接redis。所以必须要注入。

***为什么无法自动注入redis？***

#### 场景 A：Spring 托管（自动注入生效）

如果你在类上加了 `@Component` 或 `@Service`，你就是告诉 Spring：“这个类归你管。”

- Spring 启动时，它会自己 `new` 这个对象。

- Spring 发现里面有 `@Resource` 或 `@Autowired`，它会去仓库里找对应的东西填进去。

- **结果：** 注入成功。

#### 场景 B：你自己 `new`（自动注入失效）

```java
// 在 MvcConfig 里
registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate));
```

这里用了 `new` 关键字。这意味着：**是你（程序员）手动创建了这个对象，而不是 Spring。**

Spring 会说：

> “既然是你自己 new 出来的对象，那就是你的‘私生子’，不在我的管辖范围内。我不会去检查它里面有没有写 `@Resource`，我也不会帮它注入任何东西。你自己负责给它喂饭。”

这就是为什么如果你在 `RefreshTokenInterceptor` 内部写 `@Resource private StringRedisTemplate redis;`，运行起来一定是 `null`（空指针异常），因为 Spring 压根没碰过这个对象。

#### 那现在的代码是怎么实现“注入”的？

既然 Spring 不帮我们注入，那代码里是怎么做到的？

**答案是：手动传参（人工注入）。**

我们看这个流程的精妙之处：

1. **MvcConfig 是归 Spring 管的：** `MvcConfig` 上有 `@Configuration` 注解。所以 Spring 会创建它。
2. **Spring 给 MvcConfig 喂饭：** Spring 看到 `MvcConfig` 里需要 `StringRedisTemplate`（有 `@Resource`），于是 Spring 把仓库里的 Redis 递给了 `MvcConfig`。
3. **MvcConfig 当了二传手：** `MvcConfig` 拿到 Redis 后，在创建拦截器时，通过**构造函数**把 Redis 塞给了拦截器。







































