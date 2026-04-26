package com.example.vcam;

import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.util.List;

/**
 * 终端模拟器模块 - 用于执行ROOT权限下的文件操作
 * 核心功能：执行SU命令、权限验证、日志记录、错误处理
 */
public class TerminalEmulator {

    private static final String TAG = "VCAM_Terminal";
    private static TerminalEmulator instance;
    private boolean isRootAvailable = false;

    private TerminalEmulator() {
        initTerminal();
    }

    public static synchronized TerminalEmulator getInstance() {
        if (instance == null) {
            instance = new TerminalEmulator();
        }
        return instance;
    }

    /**
     * 初始化终端模拟器
     */
    private void initTerminal() {
        try {
            Log.d(TAG, "=== 初始化终端模拟器 ===");
            
            // 配置Shell
            Shell.enableVerboseLogging = true;
            Shell.setDefaultBuilder(Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(60)
            );
            
            // 检查ROOT权限
            isRootAvailable = checkRootAccess();
            Log.d(TAG, "ROOT权限状态: " + (isRootAvailable ? "可用" : "不可用"));
            
        } catch (Exception e) {
            Log.e(TAG, "初始化终端模拟器失败: " + e.getMessage());
        }
    }

    /**
     * 检查ROOT权限
     */
    public boolean checkRootAccess() {
        try {
            Log.d(TAG, "=== 检查ROOT权限 ===");
            
            // 方法1：使用Shell.rootAccess()
            boolean rootAccess = Shell.rootAccess();
            Log.d(TAG, "Shell.rootAccess()结果: " + rootAccess);
            
            if (rootAccess) {
                // 方法2：执行id命令验证
                Shell.Result idResult = Shell.su("id").exec();
                if (idResult.getCode() == 0) {
                    List<String> output = idResult.getOut();
                    if (output != null && !output.isEmpty()) {
                        String idInfo = output.get(0);
                        Log.d(TAG, "id命令输出: " + idInfo);
                        if (idInfo.contains("uid=0") || idInfo.contains("root")) {
                            Log.d(TAG, "✅ 验证为ROOT用户");
                            isRootAvailable = true;
                            return true;
                        }
                    }
                }
            }
            
            Log.d(TAG, "❌ ROOT权限验证失败");
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "ROOT权限检查异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 执行SU命令
     */
    public TerminalResult executeCommand(String command) {
        TerminalResult result = new TerminalResult();
        
        try {
            Log.d(TAG, "执行命令: " + command);
            
            // 检查ROOT权限
            if (!isRootAvailable && !checkRootAccess()) {
                result.setSuccess(false);
                result.setError("ROOT权限不可用");
                Log.e(TAG, "ROOT权限不可用，无法执行命令");
                return result;
            }
            
            // 执行命令
            Shell.Result shellResult = Shell.su(command).exec();
            
            // 处理结果
            result.setSuccess(shellResult.getCode() == 0);
            result.setExitCode(shellResult.getCode());
            result.setOutput(shellResult.getOut());
            result.setErrorOutput(shellResult.getErr());
            
            if (result.isSuccess()) {
                Log.d(TAG, "命令执行成功，退出码: " + result.getExitCode());
                if (result.getOutput() != null) {
                    for (String line : result.getOutput()) {
                        Log.d(TAG, "输出: " + line);
                    }
                }
            } else {
                Log.e(TAG, "命令执行失败，退出码: " + result.getExitCode());
                if (result.getErrorOutput() != null) {
                    for (String line : result.getErrorOutput()) {
                        Log.e(TAG, "错误: " + line);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "执行命令异常: " + e.getMessage());
            result.setSuccess(false);
            result.setError("执行异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 复制文件（使用SU权限）
     */
    public boolean copyFile(String sourcePath, String targetPath) {
        Log.d(TAG, "=== 开始复制文件 ===");
        Log.d(TAG, "源文件: " + sourcePath);
        Log.d(TAG, "目标文件: " + targetPath);
        
        try {
            // 构建复制命令
            String command = String.format("cp -f '%s' '%s'", sourcePath, targetPath);
            
            // 执行命令
            TerminalResult result = executeCommand(command);
            
            if (result.isSuccess()) {
                // 验证复制结果
                if (verifyFileCopy(sourcePath, targetPath)) {
                    Log.d(TAG, "✅ 文件复制成功并验证通过");
                    return true;
                } else {
                    Log.e(TAG, "❌ 文件复制失败：验证不通过");
                    return false;
                }
            } else {
                Log.e(TAG, "❌ 文件复制失败：" + result.getError());
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 文件复制异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证文件复制结果
     */
    private boolean verifyFileCopy(String sourcePath, String targetPath) {
        try {
            // 检查目标文件是否存在
            String checkCommand = String.format("ls -la '%s'", targetPath);
            TerminalResult checkResult = executeCommand(checkCommand);
            
            if (!checkResult.isSuccess()) {
                Log.e(TAG, "目标文件检查失败");
                return false;
            }
            
            // 检查文件大小
            String sourceSizeCommand = String.format("stat -c %%s '%s'", sourcePath);
            String targetSizeCommand = String.format("stat -c %%s '%s'", targetPath);
            
            TerminalResult sourceSizeResult = executeCommand(sourceSizeCommand);
            TerminalResult targetSizeResult = executeCommand(targetSizeCommand);
            
            if (!sourceSizeResult.isSuccess() || !targetSizeResult.isSuccess()) {
                Log.e(TAG, "文件大小检查失败");
                return false;
            }
            
            if (sourceSizeResult.getOutput() != null && !sourceSizeResult.getOutput().isEmpty() &&
                targetSizeResult.getOutput() != null && !targetSizeResult.getOutput().isEmpty()) {
                long sourceSize = Long.parseLong(sourceSizeResult.getOutput().get(0).trim());
                long targetSize = Long.parseLong(targetSizeResult.getOutput().get(0).trim());
                
                if (sourceSize == targetSize) {
                    Log.d(TAG, "文件大小验证通过: " + sourceSize + " bytes");
                    return true;
                } else {
                    Log.e(TAG, "文件大小不匹配: 源文件 " + sourceSize + " bytes, 目标文件 " + targetSize + " bytes");
                    return false;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "验证文件复制异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 创建目录（使用SU权限）
     */
    public boolean createDirectory(String directoryPath) {
        Log.d(TAG, "=== 创建目录 ===");
        Log.d(TAG, "目录路径: " + directoryPath);
        
        try {
            String command = String.format("mkdir -p '%s' && chmod 777 '%s'", directoryPath, directoryPath);
            TerminalResult result = executeCommand(command);
            
            if (result.isSuccess()) {
                // 验证目录是否创建成功
                String checkCommand = String.format("ls -la '%s'", directoryPath);
                TerminalResult checkResult = executeCommand(checkCommand);
                
                if (checkResult.isSuccess()) {
                    Log.d(TAG, "✅ 目录创建成功");
                    return true;
                } else {
                    Log.e(TAG, "❌ 目录创建失败：验证不通过");
                    return false;
                }
            } else {
                Log.e(TAG, "❌ 目录创建失败：" + result.getError());
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 目录创建异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String filePath) {
        try {
            String command = String.format("test -f '%s' && echo 'exists' || echo 'not exists'", filePath);
            TerminalResult result = executeCommand(command);
            
            if (result.isSuccess() && result.getOutput() != null && !result.getOutput().isEmpty()) {
                return result.getOutput().get(0).trim().equals("exists");
            }
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "检查文件存在性异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查目录是否存在
     */
    public boolean directoryExists(String directoryPath) {
        try {
            String command = String.format("test -d '%s' && echo 'exists' || echo 'not exists'", directoryPath);
            TerminalResult result = executeCommand(command);
            
            if (result.isSuccess() && result.getOutput() != null && !result.getOutput().isEmpty()) {
                return result.getOutput().get(0).trim().equals("exists");
            }
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "检查目录存在性异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取ROOT权限状态
     */
    public boolean isRootAvailable() {
        // 每次调用都重新检查ROOT权限，确保状态是最新的
        return checkRootAccess();
    }

    /**
     * 终端命令执行结果
     */
    public static class TerminalResult {
        private boolean success;
        private int exitCode;
        private List<String> output;
        private List<String> errorOutput;
        private String error;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getExitCode() {
            return exitCode;
        }

        public void setExitCode(int exitCode) {
            this.exitCode = exitCode;
        }

        public List<String> getOutput() {
            return output;
        }

        public void setOutput(List<String> output) {
            this.output = output;
        }

        public List<String> getErrorOutput() {
            return errorOutput;
        }

        public void setErrorOutput(List<String> errorOutput) {
            this.errorOutput = errorOutput;
        }

        public String getError() {
            if (error != null) {
                return error;
            }
            if (errorOutput != null && !errorOutput.isEmpty()) {
                return String.join("\n", errorOutput);
            }
            return "未知错误";
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}