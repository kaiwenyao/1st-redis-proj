### LUA脚本巨大BUG

每次重新运行项目时lua脚本不会参与build。

必须在Idea上面的build -> rebuild project 

之后在maven中 lifecycle -> clean -> install

然后才可以正常运行项目

