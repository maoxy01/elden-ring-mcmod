# 2026-10-17 黑屏日志核查

本轮仅核查日志和系统状态，暂停模组构建、客户端与专服测试；没有修改分页文件、驱动或其它系统设置。约15:05检查时无java/javaw进程，计划中的final-build.log也未生成，不能把中断的构建记为执行成功。

## 判断

已确认此前发生系统级提交内存耗尽，而非单独Java堆容量不足。它是目前与整机失去响应最相关的已证实异常；仅凭现有日志仍不能证明它就是黑屏的唯一直接原因，不能认定显卡或硬件损坏。

先前本项目同时运行客户端、专服与Gradle，确实增加了系统内存压力。第一次原生内存失败后应立即停止运行验证；后来虽然限制堆内存并改为单JVM，仍不足以替代整机资源检查。后续运行验证维持暂停，先处理资源预算和启动前检查。

## 证据时间线（本地时间）

- 13:22:57：System事件2004明确报告虚拟内存不足，列出java.exe PID29956约2.20GiB、java.exe PID40620约1.33GiB及DeepSeek Harness约1.28GiB。这是系统列出的部分主要进程，不能当作所有进程的总占用。
- 13:22:58：客户端、专服、Gradle及包装器先后出现原生内存分配失败。包装器连32744字节都无法分配；其日志显示系统提交余量约12MiB，客户端约10MiB。
- 13:25:57：再次出现2004，涉及客户端PID22852与专服PID45840；Windows Error Reporting服务同时因“页面文件太小，无法完成操作”停止。
- 13:26:02：专服PID45840无法分配296656字节，JVM日志中AvailPageFile为0M。该字段是Windows系统提交余量，不能误称为物理RAM余量或实际磁盘分页文件大小。
- 13:39:12：最后一次单JVM游戏日志记录正常保存所有维度、停止，Gradle最终成功；因此没有发现这次游戏本身在黑屏时刻抛出的新崩溃栈。
- 14:35:26：System事件7023再次记录Windows Error Reporting因页面文件太小停止；14:38:25服务连接超时。
- 14:47:52：当前系统启动时间；14:47:56记录Kernel-Power 41，BugcheckCode=0、WHEABootErrorCount=0。
- 14:48:13：EventLog 6008记录非正常关机，并将上次异常关闭时间标为14:07:32。这个记录不能精确确定用户看到黑屏的起始时刻，也不能单凭41判断断电原因。

13:00至检查时段的System事件筛选未发现Display/nvlddmkm显示驱动复位、WHEA或WER-SystemErrorReporting蓝屏记录。该结果不排除无日志的驱动卡死，也不构成硬件健康证明。

## 重启后的状态

约15:05：物理内存约15.7GiB，AvailableMBytes=6813；系统CommittedBytes约13.4GiB / CommitLimit约29.2GiB，占45%。D盘分页文件已启用，当前分配13824MiB；不能诊断为“没有开启虚拟内存”。AutomaticManagedPagefile=false，而单独分页文件InitialSize/MaximumSize均为0，仅记录当前观测，不推断成固定容量配置。

当前状态已恢复，不代表旧故障已被修复。现有证据没有定位到某一个模组的内存泄漏，也不能用MemorySweep替代系统提交额度诊断。

## 原始证据

续工记录（2026-10-18）：人类随后明确继续模组整合，已恢复受内存门槛约束的串行验收。新增启动检查不等于根因已修复；本轮提交余量约4.2GiB，不满足客户端12GiB门槛，未启动游戏。未修改系统分页文件，也未自动关闭其它软件。

- `research-evidence/modpack-expanded-20261017/black-screen-20261017/system-events.json`
- `research-evidence/modpack-expanded-20261017/black-screen-20261017/current-state.json`
- `research-evidence/modpack-expanded-20261017/black-screen-20261017/kernel-power.xml`
- `research-evidence/modpack-expanded-20261017/black-screen-20261017/application-raw.xml.txt`
- 根目录`hs_err_pid100108.log`、`hs_err_pid88904.log`
- `build/modpack-expanded-20261017/client/hs_err_pid29956.log`
- `build/modpack-expanded-20261017/server/hs_err_pid40620.log`、`hs_err_pid45840.log`
- `build/modpack-expanded-20261017/single-integration.log`
