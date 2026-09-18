import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortLookupUtil {
    // 统一界面字体大小
    private static final int UI_FONT_SIZE = 15;

    // 命令执行超时时间（秒），避免外部命令无响应时界面一直等待
    private static final long COMMAND_TIMEOUT_SECONDS = 15;

    // 禁止结束的系统关键进程：0 = System Idle Process，4 = System（结束会导致系统崩溃）
    private static final String[] PROTECTED_PIDS = {"0", "4"};

    /**
     * tasklist 数据行解析规则：映像名称 PID 会话名 会话# 内存占用。
     * 映像名称可能含空格（如 System Idle Process），内存占用可能含空格与单位（如 4,852 K），
     * 因此依次匹配“含空格进程名 + 数字 PID + 会话名 + 数字会话# + 内存数值 + 可选单位”。
     */
    private static final Pattern TASKLIST_ROW = Pattern.compile(
            "^(.+?)\\s+(\\d+)\\s+(\\S+)\\s+(\\d+)\\s+([\\d,]+)\\s*([KMG]?)$");

    private JFrame frame;
    private JTextField portField;
    private JButton searchButton;
    private JLabel statusLabel;
    private JTable resultList;
    private DefaultTableModel tableModel;

    public PortLookupUtil() {
        initialize();
    }

    /**
     * 解析界面统一字体：优先微软雅黑，缺失时回退到逻辑字体。
     * 对字体名称做校验，避免 Java 静默回退到默认字体后误判为已找到。
     */
    private static Font uiFont() {
        for (String name : new String[]{"Microsoft YaHei UI", "Microsoft YaHei"}) {
            Font font = new Font(name, Font.PLAIN, UI_FONT_SIZE);
            if (font.getFamily().equalsIgnoreCase(name)) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, UI_FONT_SIZE);
    }

    /**
     * 将 netstat 的英文状态值翻译为中文，未收录的状态原样返回。
     */
    private static String translateState(String state) {
        switch (state) {
            case "LISTENING":
                return "监听中";
            case "ESTABLISHED":
                return "已建立";
            case "TIME_WAIT":
                return "等待关闭";
            case "CLOSE_WAIT":
                return "被动关闭等待";
            case "SYN_SENT":
                return "同步已发送";
            case "SYN_RECEIVED":
                return "同步已接收";
            case "FIN_WAIT_1":
            case "FIN_WAIT_2":
                return "等待结束";
            case "LAST_ACK":
                return "最后确认";
            case "CLOSING":
                return "关闭中";
            case "CLOSE":
                return "已关闭";
            case "DELETE_TCB":
                return "删除 TCB";
            default:
                return state;
        }
    }

    /**
     * 是否为禁止结束的系统关键进程。
     */
    private static boolean isProtectedPid(String pid) {
        for (String protectedPid : PROTECTED_PIDS) {
            if (protectedPid.equals(pid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 netstat 的本地地址中取出端口号，形如 0.0.0.0:135、[::]:135、127.0.0.1:8080。
     * 无法解析时返回 -1。
     */
    private static int extractLocalPort(String localAddress) {
        int index = localAddress.lastIndexOf(':');
        if (index < 0 || index == localAddress.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(localAddress.substring(index + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 命令执行结果：退出码与全部输出行。
     */
    private static final class CommandResult {
        private final int exitCode;
        private final List<String> lines;

        private CommandResult(int exitCode, List<String> lines) {
            this.exitCode = exitCode;
            this.lines = lines;
        }
    }

    /**
     * 执行外部命令并读取输出。
     * 直接以参数数组调用（不经过 cmd），因此参数中的特殊字符不会被解释为命令行语法。
     */
    private static CommandResult runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true); // 合并错误流，避免未读取错误流导致阻塞
        Process process = builder.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("命令执行超时：" + String.join(" ", command));
        }
        return new CommandResult(process.exitValue(), lines);
    }

    private void initialize() {
        frame = new JFrame("端口查找工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 520);
        frame.setLayout(new GridBagLayout());

        // 设置窗口居中显示
        frame.setLocationRelativeTo(null);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 设置组件之间的间距

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());

        JLabel label = new JLabel("端口:");
        label.setFont(uiFont()); // 统一字体
        panel.add(label);

        portField = new JTextField(10);
        portField.setFont(uiFont()); // 统一字体
        panel.add(portField);

        // 添加 ActionListener 以支持回车键触发搜索
        portField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchPort();
            }
        });

        searchButton = new JButton("查找");
        searchButton.setFont(uiFont()); // 统一字体
        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchPort();
            }
        });
        panel.add(searchButton);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER; // 占据整行
        gbc.fill = GridBagConstraints.HORIZONTAL; // 允许水平扩展
        frame.add(panel, gbc);

        tableModel = new DefaultTableModel(new Object[]{"协议", "本地地址", "外部地址", "状态", "PID"}, 0);
        resultList = new JTable(tableModel);
        resultList.getTableHeader().setReorderingAllowed(false); // 禁止表头重新排序
        resultList.getTableHeader().setResizingAllowed(false); // 禁止调整列宽

        TableColumnModel columnModel = resultList.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(100); // 设置列宽
        columnModel.getColumn(1).setPreferredWidth(150);
        columnModel.getColumn(2).setPreferredWidth(150);
        columnModel.getColumn(3).setPreferredWidth(100);
        columnModel.getColumn(4).setPreferredWidth(50);

        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                    int row = resultList.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        resultList.setRowSelectionInterval(row, row); // 选中右击的那一行
                        String protocol = String.valueOf(tableModel.getValueAt(row, 0));
                        String localAddress = String.valueOf(tableModel.getValueAt(row, 1));
                        String pid = String.valueOf(tableModel.getValueAt(row, 4));
                        // 用于确认对话框展示的连接描述
                        String description = protocol + "  " + localAddress;

                        // 创建弹出菜单
                        JPopupMenu popupMenu = new JPopupMenu();
                        JMenuItem killProcessItem = new JMenuItem("结束进程");
                        JMenuItem viewProgramItem = new JMenuItem("查看程序");

                        // 添加事件监听器
                        killProcessItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                endProcess(pid, description);
                            }
                        });

                        viewProgramItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                viewProgram(pid);
                            }
                        });

                        // 将菜单项添加到弹出菜单
                        popupMenu.add(killProcessItem);
                        popupMenu.add(viewProgramItem);

                        // 显示弹出菜单
                        popupMenu.show(resultList, e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultList);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER; // 占据整行
        gbc.weightx = 1.0; // 允许水平扩展
        gbc.weighty = 1.0; // 允许垂直扩展
        gbc.fill = GridBagConstraints.BOTH; // 允许填充
        frame.add(scrollPane, gbc);

        statusLabel = new JLabel("请输入端口号后点击查找。");
        statusLabel.setFont(uiFont()); // 统一字体
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.weighty = 0; // 状态栏不参与垂直扩展
        gbc.fill = GridBagConstraints.HORIZONTAL;
        frame.add(statusLabel, gbc);

        frame.setVisible(true);
    }

    /**
     * 读取输入框中的端口号并校验，校验通过后发起查询。
     */
    private void searchPort() {
        String text = portField.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请输入端口号。", "错误", JOptionPane.ERROR_MESSAGE);
            portField.requestFocusInWindow();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame, "端口号必须是 1 到 65535 之间的整数，当前输入：" + text + "。",
                    "错误", JOptionPane.ERROR_MESSAGE);
            portField.requestFocusInWindow();
            return;
        }

        if (port < 1 || port > 65535) {
            JOptionPane.showMessageDialog(frame, "端口号必须在 1 到 65535 之间，当前输入：" + port + "。",
                    "错误", JOptionPane.ERROR_MESSAGE);
            portField.requestFocusInWindow();
            return;
        }

        startSearch(port);
    }

    /**
     * 在后台线程查询指定端口，避免阻塞界面。
     */
    private void startSearch(final int port) {
        searchButton.setEnabled(false);
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        statusLabel.setText("正在查找本地端口 " + port + " …");

        SwingWorker<List<Object[]>, Void> worker = new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                CommandResult result = runCommand("netstat", "-ano");
                if (result.exitCode != 0) {
                    throw new IOException("netstat 执行失败（退出码 " + result.exitCode + "）。");
                }
                return buildRowsFromNetstat(result.lines, port);
            }

            @Override
            protected void done() {
                searchButton.setEnabled(true);
                frame.setCursor(Cursor.getDefaultCursor());
                try {
                    List<Object[]> rows = get();
                    tableModel.setRowCount(0);
                    for (Object[] row : rows) {
                        tableModel.addRow(row);
                    }
                    if (rows.isEmpty()) {
                        statusLabel.setText("未发现占用本地端口 " + port + " 的连接记录。");
                    } else {
                        statusLabel.setText("本地端口 " + port + " 共 " + rows.size() + " 条记录。");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断状态
                    statusLabel.setText("查询已中断。");
                } catch (ExecutionException e) {
                    tableModel.setRowCount(0);
                    Throwable cause = e.getCause();
                    String message = (cause == null ? e.toString() : cause.getMessage());
                    statusLabel.setText("查询失败。");
                    JOptionPane.showMessageDialog(frame, "查询端口失败：" + message, "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * 从 netstat 输出中筛选本地端口精确等于目标端口的行。
     * <p>
     * 不再使用 findstr 做子串匹配，否则查找 80 会误匹配 8080、远端端口 80 等无关行。
     */
    private static List<Object[]> buildRowsFromNetstat(List<String> lines, int port) {
        List<Object[]> rows = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 4) {
                continue; // 跳过空行、标题行（“活动连接”“Active Connections”）与列名行
            }

            String protocol = parts[0];
            if (!protocol.equalsIgnoreCase("TCP") && !protocol.equalsIgnoreCase("UDP")) {
                continue; // 只保留数据行
            }

            if (extractLocalPort(parts[1]) != port) {
                continue; // 本地端口必须精确相等
            }

            String pid = parts[parts.length - 1];
            if (protocol.equalsIgnoreCase("TCP") && parts.length >= 5) {
                // TCP 行: 协议 本地地址 外部地址 状态 PID
                rows.add(new Object[]{protocol, parts[1], parts[2], translateState(parts[3]), pid});
            } else {
                // UDP 行: 协议 本地地址 外部地址 PID（无状态列）
                rows.add(new Object[]{protocol, parts[1], parts[2], "", pid});
            }
        }
        return rows;
    }

    /**
     * 结束指定 PID 的进程：先做安全校验与确认，再在后台执行。
     *
     * @param pid         进程号
     * @param description 连接描述，用于确认对话框
     */
    private void endProcess(final String pid, String description) {
        if (pid == null || pid.trim().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "无效的 PID：" + pid, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (isProtectedPid(pid)) {
            JOptionPane.showMessageDialog(frame,
                    "PID " + pid + " 是系统关键进程，结束它可能导致系统崩溃，已拒绝该操作。",
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 结束进程不可撤销，先确认
        int choice = JOptionPane.showConfirmDialog(frame,
                "确定要结束以下进程吗？\n\n连接：" + description + "\nPID：" + pid + "\n\n此操作不可撤销。",
                "确认结束进程", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }

        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<CommandResult, Void> worker = new SwingWorker<CommandResult, Void>() {
            @Override
            protected CommandResult doInBackground() throws Exception {
                return runCommand("taskkill", "/PID", pid, "/F");
            }

            @Override
            protected void done() {
                frame.setCursor(Cursor.getDefaultCursor());
                try {
                    CommandResult result = get();
                    if (result.exitCode == 0) {
                        JOptionPane.showMessageDialog(frame, "已结束 PID " + pid + " 的进程。",
                                "成功", JOptionPane.INFORMATION_MESSAGE);
                        // 重新加载端口列表（仅当输入框仍是合法端口时）
                        int current = currentPortOrZero();
                        if (current > 0) {
                            startSearch(current);
                        }
                    } else {
                        JOptionPane.showMessageDialog(frame,
                                "无法结束 PID " + pid + " 的进程（退出码 " + result.exitCode + "）。\n"
                                        + formatOutput(result.lines),
                                "错误", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断状态
                    JOptionPane.showMessageDialog(frame, "结束进程操作已中断。", "错误", JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String message = (cause == null ? e.toString() : cause.getMessage());
                    JOptionPane.showMessageDialog(frame, "结束 PID " + pid + " 的进程时发生错误：" + message,
                            "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * 读取输入框中当前端口，非法或为空时返回 0。
     */
    private int currentPortOrZero() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            return (port >= 1 && port <= 65535) ? port : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将命令输出整理为可展示的文本。
     */
    private static String formatOutput(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                sb.append(line.trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * 查看占用指定 PID 的程序信息：在后台执行 tasklist，完成后弹出信息对话框。
     */
    private void viewProgram(final String pid) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<CommandResult, Void> worker = new SwingWorker<CommandResult, Void>() {
            @Override
            protected CommandResult doInBackground() throws Exception {
                return runCommand("tasklist", "/FI", "PID eq " + pid);
            }

            @Override
            protected void done() {
                frame.setCursor(Cursor.getDefaultCursor());
                try {
                    CommandResult result = get();
                    if (result.exitCode != 0) {
                        // 例如“错误: 拒绝访问”，此时不能只显示空表格
                        String output = formatOutput(result.lines);
                        JOptionPane.showMessageDialog(frame,
                                "查询程序信息失败（退出码 " + result.exitCode + "）。"
                                        + (output.isEmpty() ? "" : "\n" + output),
                                "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    showProgramDialog(pid, result.lines);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // 恢复中断状态
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String message = (cause == null ? e.toString() : cause.getMessage());
                    JOptionPane.showMessageDialog(frame, "查询程序信息时发生错误：" + message,
                            "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * 展示程序信息对话框。
     */
    private void showProgramDialog(String pid, List<String> outputLines) {
        // 创建自定义对话框
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // 将输出内容转换为表格形式
        String[] headers = {"映像名称", "PID", "会话名", "会话#", "内存占用"};
        List<Object[]> rows = new ArrayList<>();

        for (String outputLine : outputLines) {
            String trimmed = outputLine.trim();
            if (trimmed.isEmpty()) {
                continue; // 跳过空行
            }
            // 按 tasklist 列结构解析，兼容“含空格的进程名”与“含空格/带单位的内存占用”，
            // 表头行、分隔线等无法匹配的行（如含中文提示的行）自动跳过。
            Matcher matcher = TASKLIST_ROW.matcher(trimmed);
            if (!matcher.matches()) {
                continue;
            }
            String imageName = matcher.group(1);
            String pidValue = matcher.group(2);
            String sessionName = matcher.group(3);
            String sessionNum = matcher.group(4);
            String memUsage = matcher.group(5) + (matcher.group(6).isEmpty() ? "" : " " + matcher.group(6));
            rows.add(new Object[]{imageName, pidValue, sessionName, sessionNum, memUsage});
        }

        if (rows.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "未找到 PID " + pid + " 对应的进程信息。",
                    "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DefaultTableModel programTableModel = new DefaultTableModel(rows.toArray(new Object[0][]), headers);
        JTable table = new JTable(programTableModel);
        table.getTableHeader().setReorderingAllowed(false); // 禁止表头重新排序
        table.getTableHeader().setResizingAllowed(false); // 禁止调整列宽

        TableColumnModel columnModel = table.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(200); // 调整列宽
        columnModel.getColumn(1).setPreferredWidth(100);
        columnModel.getColumn(2).setPreferredWidth(150);
        columnModel.getColumn(3).setPreferredWidth(100);
        columnModel.getColumn(4).setPreferredWidth(150);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // 创建按钮面板
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout()); // 使用 GridBagLayout 替换 FlowLayout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.CENTER; // 设置按钮居中

        // 创建对话框
        JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
        JDialog dialog = optionPane.createDialog(frame, "占用程序");
        dialog.setFont(uiFont()); // 确保对话框使用正确的字体

        // 设置对话框中的所有组件使用相同的字体
        Font font = uiFont();
        for (Component component : panel.getComponents()) {
            if (component instanceof JComponent) {
                ((JComponent) component).setFont(font);
            }
        }

        // 创建“结束进程”按钮
        JButton killProcessButton = new JButton("结束进程");
        killProcessButton.setFont(uiFont()); // 统一字体
        killProcessButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
                endProcess(pid, "PID " + pid);
            }
        });
        buttonPanel.add(killProcessButton, gbc);

        // 创建“确定”按钮
        JButton viewProgramButton = new JButton("确定");
        viewProgramButton.setFont(uiFont()); // 统一字体
        viewProgramButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        gbc.gridx = 1; // 将“确定”按钮放在同一行的右侧
        buttonPanel.add(viewProgramButton, gbc);

        // 将按钮面板添加到对话框
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        // 确保对话框中的所有组件使用相同的字体
        for (Component component : buttonPanel.getComponents()) {
            if (component instanceof JComponent) {
                ((JComponent) component).setFont(font);
            }
        }

        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        // 强制界面使用中文，使 JOptionPane 等 JDK 内置组件在非中文系统上同样显示中文
        Locale.setDefault(Locale.CHINA);

        // java PortLookupUtil
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new PortLookupUtil();
            }
        });
    }
}
