package com.example.vcam;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.vcam.TerminalEmulator.TerminalResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端模拟器Activity
 * 功能：实时显示系统命令的执行状态、输出结果及错误信息
 * 重点监控ROOT权限执行的文件复制操作
 */
public class TerminalActivity extends Activity {

    private static final String TAG = "VCAM_TerminalActivity";
    
    private TerminalEmulator terminal;
    private Handler mainHandler;
    
    // UI组件
    private ScrollView outputScrollView;
    private TextView outputTextView;
    private EditText commandEditText;
    private Button executeButton;
    private ListView commandHistoryListView;
    
    // 命令历史记录
    private List<String> commandHistory;
    private ArrayAdapter<String> historyAdapter;
    
    // 颜色常量
    private static final int COLOR_SUCCESS = 0xFF4CAF50; // 绿色
    private static final int COLOR_ERROR = 0xFFF44336;   // 红色
    private static final int COLOR_WARNING = 0xFFFF9800; // 橙色
    private static final int COLOR_INFO = 0xFF2196F3;    // 蓝色
    private static final int COLOR_DEFAULT = 0xFF333333; // 黑色
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        
        initViews();
        initTerminal();
        initCommandHistory();
        initListeners();
        
        // 显示欢迎信息
        appendOutput("欢迎使用VCAM终端模拟器\n", COLOR_INFO);
        appendOutput("提示：本终端支持ROOT权限操作\n", COLOR_INFO);
        appendOutput("输入 'help' 查看可用命令\n", COLOR_INFO);
        appendOutput("==================================\n", COLOR_INFO);
        
        // 检查ROOT权限
        checkRootStatus();
    }
    
    private void initViews() {
        outputScrollView = findViewById(R.id.outputScrollView);
        outputTextView = findViewById(R.id.outputTextView);
        commandEditText = findViewById(R.id.commandEditText);
        executeButton = findViewById(R.id.executeButton);
        commandHistoryListView = findViewById(R.id.commandHistoryListView);
    }
    
    private void initTerminal() {
        terminal = TerminalEmulator.getInstance();
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    private void initCommandHistory() {
        commandHistory = new ArrayList<>();
        historyAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, commandHistory);
        commandHistoryListView.setAdapter(historyAdapter);
    }
    
    private void initListeners() {
        // 执行命令按钮
        executeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                executeCommand();
            }
        });
        
        // 命令历史点击
        commandHistoryListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String command = commandHistory.get(position);
                commandEditText.setText(command);
            }
        });
        
        // 键盘回车执行命令
        commandEditText.setOnEditorActionListener((v, actionId, event) -> {
            executeCommand();
            return true;
        });
    }
    
    private void checkRootStatus() {
        new Thread(() -> {
            boolean rootAvailable = terminal.isRootAvailable();
            mainHandler.post(() -> {
                if (rootAvailable) {
                    appendOutput("✅ ROOT权限可用\n", COLOR_SUCCESS);
                } else {
                    appendOutput("⚠️  ROOT权限不可用\n", COLOR_WARNING);
                    appendOutput("某些命令可能无法执行\n", COLOR_WARNING);
                }
            });
        }).start();
    }
    
    private void executeCommand() {
        String command = commandEditText.getText().toString().trim();
        if (command.isEmpty()) {
            return;
        }
        
        // 添加到历史记录
        addToHistory(command);
        
        // 显示命令
        appendOutput("$ " + command + "\n", COLOR_INFO);
        
        // 特殊命令处理
        if (command.equals("help")) {
            showHelp();
            return;
        } else if (command.equals("clear")) {
            clearOutput();
            return;
        } else if (command.equals("root")) {
            checkRootStatus();
            return;
        }
        
        // 执行命令
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 执行命令
                TerminalResult result = terminal.executeCommand(command);
                
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                
                // 显示结果
                mainHandler.post(() -> {
                    if (result.isSuccess()) {
                        appendOutput("执行时间: " + executionTime + "ms\n", COLOR_INFO);
                        if (result.getOutput() != null && !result.getOutput().isEmpty()) {
                            for (String line : result.getOutput()) {
                                appendOutput(line + "\n", COLOR_DEFAULT);
                            }
                        }
                        appendOutput("✅ 命令执行成功\n", COLOR_SUCCESS);
                    } else {
                        appendOutput("执行时间: " + executionTime + "ms\n", COLOR_INFO);
                        if (result.getErrorOutput() != null && !result.getErrorOutput().isEmpty()) {
                            for (String line : result.getErrorOutput()) {
                                appendOutput(line + "\n", COLOR_ERROR);
                            }
                        }
                        appendOutput("❌ 命令执行失败: " + result.getError() + "\n", COLOR_ERROR);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "执行命令异常: " + e.getMessage());
                mainHandler.post(() -> {
                    appendOutput("❌ 执行异常: " + e.getMessage() + "\n", COLOR_ERROR);
                });
            }
        }).start();
        
        // 清空输入框
        commandEditText.setText("");
    }
    
    private void showHelp() {
        appendOutput("可用命令:\n", COLOR_INFO);
        appendOutput("  help    - 显示帮助信息\n", COLOR_DEFAULT);
        appendOutput("  clear   - 清空输出\n", COLOR_DEFAULT);
        appendOutput("  root    - 检查ROOT权限状态\n", COLOR_DEFAULT);
        appendOutput("  ls      - 列出目录内容\n", COLOR_DEFAULT);
        appendOutput("  cp      - 复制文件\n", COLOR_DEFAULT);
        appendOutput("  mkdir   - 创建目录\n", COLOR_DEFAULT);
        appendOutput("  rm      - 删除文件\n", COLOR_DEFAULT);
        appendOutput("  cat     - 查看文件内容\n", COLOR_DEFAULT);
        appendOutput("  id      - 查看当前用户权限\n", COLOR_DEFAULT);
        appendOutput("  pwd     - 显示当前目录\n", COLOR_DEFAULT);
        appendOutput("  df      - 查看磁盘空间\n", COLOR_DEFAULT);
    }
    
    private void clearOutput() {
        outputTextView.setText("");
        appendOutput("终端已清空\n", COLOR_INFO);
    }
    
    private void addToHistory(String command) {
        // 避免重复命令
        if (!commandHistory.contains(command)) {
            commandHistory.add(0, command);
            // 限制历史记录数量
            if (commandHistory.size() > 50) {
                commandHistory.remove(commandHistory.size() - 1);
            }
            historyAdapter.notifyDataSetChanged();
        }
    }
    
    private void appendOutput(String text, int color) {
        SpannableStringBuilder builder = new SpannableStringBuilder(outputTextView.getText());
        SpannableStringBuilder newText = new SpannableStringBuilder(text);
        newText.setSpan(new ForegroundColorSpan(color), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.append(newText);
        outputTextView.setText(builder);
        
        // 自动滚动到底部
        outputScrollView.post(() -> outputScrollView.fullScroll(View.FOCUS_DOWN));
    }
    
    /**
     * 执行文件复制操作（带进度显示）
     */
    public void executeFileCopy(String sourcePath, String targetPath) {
        appendOutput("\n开始复制文件...\n", COLOR_INFO);
        appendOutput("源文件: " + sourcePath + "\n", COLOR_DEFAULT);
        appendOutput("目标文件: " + targetPath + "\n", COLOR_DEFAULT);
        
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // 检查ROOT权限
                if (!terminal.isRootAvailable()) {
                    mainHandler.post(() -> {
                        appendOutput("⚠️  ROOT权限不可用，尝试使用标准API\n", COLOR_WARNING);
                    });
                }
                
                // 执行复制
                boolean success = terminal.copyFile(sourcePath, targetPath);
                
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                
                mainHandler.post(() -> {
                    appendOutput("执行时间: " + executionTime + "ms\n", COLOR_INFO);
                    if (success) {
                        appendOutput("✅ 文件复制成功\n", COLOR_SUCCESS);
                    } else {
                        appendOutput("❌ 文件复制失败\n", COLOR_ERROR);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "复制文件异常: " + e.getMessage());
                mainHandler.post(() -> {
                    appendOutput("❌ 复制异常: " + e.getMessage() + "\n", COLOR_ERROR);
                });
            }
        }).start();
    }
}