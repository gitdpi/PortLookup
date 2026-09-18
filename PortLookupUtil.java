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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortLookupUtil {
    // 统一界面字体大小
    private static final int UI_FONT_SIZE = 15;

    /**
     * tasklist 数据行解析规则：映像名称 PID 会话名 会话# 内存占用。
     * 映像名称可能含空格（如 System Idle Process），内存占用可能含空格与单位（如 4,852 K），
     * 因此依次匹配“含空格进程名 + 数字 PID + 会话名 + 数字会话# + 内存数值 + 可选单位”。
     */
    private static final Pattern TASKLIST_ROW = Pattern.compile(
            "^(.+?)\\s+(\\d+)\\s+(\\S+)\\s+(\\d+)\\s+([\\d,]+)\\s*([KMG]?)$");

    private JFrame frame;
    private JTextField portField;
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

    private void initialize() {
        frame = new JFrame("端口查找工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
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

        JButton searchButton = new JButton("查找");
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
                        String pid = tableModel.getValueAt(row, 4).toString(); // 确保正确提取 pid
                        System.out.println("Extracted PID for kill action: " + pid); // 添加调试信息

                        // 创建弹出菜单
                        JPopupMenu popupMenu = new JPopupMenu();
                        JMenuItem killProcessItem = new JMenuItem("结束进程");
                        JMenuItem viewProgramItem = new JMenuItem("查看程序");

                        // 添加事件监听器
                        killProcessItem.addActionListener(new ActionListener() {
                            @Override
                            public void actionPerformed(ActionEvent e) {
                                endProcess(pid);
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

        frame.setVisible(true);
    }

    private void searchPort() {
        String port = portField.getText();
        if (port.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请输入端口号。", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        tableModel.setRowCount(0); // 清空表格
        try {
            System.out.println("Executing command: netstat -ano | findstr :" + port);
            Process process = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr :" + port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 5) {
                    // TCP 行: 协议 本地地址 外部地址 状态 PID
                    tableModel.addRow(new Object[]{parts[0], parts[1], parts[2], translateState(parts[3]), parts[4]});
                    System.out.println("Output line: " + line);
                    System.out.println("Extracted PID: " + parts[4]); // 添加调试信息
                } else if (parts.length == 4) {
                    // UDP 行: 协议 本地地址 外部地址 PID（无状态列）
                    tableModel.addRow(new Object[]{parts[0], parts[1], parts[2], "", parts[3]});
                    System.out.println("Output line: " + line);
                    System.out.println("Extracted PID: " + parts[3]); // 添加调试信息
                }
            }
            System.out.println("Number of elements in tableModel: " + tableModel.getRowCount());
            System.out.println("JTable updated with " + resultList.getModel().getRowCount() + " elements.");

            System.out.println("JTable model set to tableModel with " + resultList.getModel().getRowCount() + " elements."); // 添加调试信息
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("An error occurred while executing the command.");
        }
    }

    private void endProcess(String pid) {
        if (pid == null || pid.trim().isEmpty()) {
            // 添加调试信息，检查 PID 是否为空或空白字符串
            System.out.println("Invalid PID: " + pid);
            JOptionPane.showMessageDialog(frame, "无效的 PID：" + pid, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Process process = Runtime.getRuntime().exec("taskkill /PID " + pid + " /F");
            int exitCode = process.waitFor(); // 等待命令执行完成并获取退出码
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(frame, "已结束 PID " + pid + " 的进程。", "成功", JOptionPane.INFORMATION_MESSAGE);
                // 重新加载端口列表
                searchPort();
            } else {
                // 添加调试信息，检查命令的返回值
                System.out.println("Failed to terminate process with PID " + pid + ". Exit code: " + exitCode);
                JOptionPane.showMessageDialog(frame, "无法结束 PID " + pid + " 的进程（退出码 " + exitCode + "）。", "错误", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            // 添加调试信息，检查异常
            System.out.println("An error occurred while terminating process with PID " + pid);
            JOptionPane.showMessageDialog(frame, "结束 PID " + pid + " 的进程时发生错误。", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void viewProgram(String pid) {
        try {
            Process process = Runtime.getRuntime().exec("tasklist /FI \"PID eq " + pid + "\"");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK")); // 指定编码格式为 GBK
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // 创建自定义对话框
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            // 将输出内容转换为表格形式
            String[] headers = {"映像名称", "PID", "会话名", "会话#", "内存占用"};
            String[] lines = output.toString().split("\n");
            List<Object[]> rows = new ArrayList<>();

            for (String outputLine : lines) {
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
                    endProcess(pid);
                    // 关闭对话框
                    dialog.dispose();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
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
