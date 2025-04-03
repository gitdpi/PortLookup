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

public class PortLookupUtil {
    private JFrame frame;
    private JTextField portField;
    private JTable resultList;
    private DefaultTableModel tableModel;

    public PortLookupUtil() {
        initialize();
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

        JLabel label = new JLabel("Port:");
        label.setFont(new Font("Arial", Font.PLAIN, 15)); // 统一字体大小
        panel.add(label);

        portField = new JTextField(10);
        portField.setFont(new Font("Arial", Font.PLAIN, 15)); // 统一字体大小
        panel.add(portField);

        // 添加 ActionListener 以支持回车键触发搜索
        portField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchPort();
            }
        });

        JButton searchButton = new JButton("Search");
        searchButton.setFont(new Font("Arial", Font.PLAIN, 15)); // 统一字体大小
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

        tableModel = new DefaultTableModel(new Object[]{"Protocol", "Local Address", "Foreign Address", "State", "PID"}, 0);
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
                        JMenuItem killProcessItem = new JMenuItem("Kill Process"); // 修改按钮文本
                        JMenuItem viewProgramItem = new JMenuItem("View Program"); // 修改按钮文本

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
            JOptionPane.showMessageDialog(frame, "Please enter a port number.", "Error", JOptionPane.ERROR_MESSAGE);
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
                if (parts.length > 4) {
                    String pid = parts[4];
                    tableModel.addRow(parts); // 添加行数据
                    System.out.println("Output line: " + line); 
                    System.out.println("Extracted PID: " + pid); // 添加调试信息
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
            JOptionPane.showMessageDialog(frame, "Invalid PID: " + pid, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Process process = Runtime.getRuntime().exec("taskkill /PID " + pid + " /F");
            int exitCode = process.waitFor(); // 等待命令执行完成并获取退出码
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(frame, "Process with PID " + pid + " has been terminated.", "Success", JOptionPane.INFORMATION_MESSAGE);
                // 重新加载端口列表
                searchPort();
            } else {
                // 添加调试信息，检查命令的返回值
                System.out.println("Failed to terminate process with PID " + pid + ". Exit code: " + exitCode);
                JOptionPane.showMessageDialog(frame, "Failed to terminate process with PID " + pid + ". Exit code: " + exitCode, "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            // 添加调试信息，检查异常
            System.out.println("An error occurred while terminating process with PID " + pid);
            JOptionPane.showMessageDialog(frame, "An error occurred while terminating process with PID " + pid, "Error", JOptionPane.ERROR_MESSAGE);
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
            String[] headers = {"Image Name", "PID", "Session Name", "Session#", "Mem Usage"};
            String[] lines = output.toString().split("\n");
            String[][] data = new String[lines.length - 2][headers.length]; // 去掉标题行和空行

            for (int i = 2; i < lines.length; i++) {
                data[i - 2] = lines[i].trim().split("\\s+", headers.length);
            }

            DefaultTableModel tableModel = new DefaultTableModel(data, headers);
            JTable table = new JTable(tableModel);
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
            dialog.setFont(new Font("Arial", Font.PLAIN, 15)); // 确保对话框使用正确的字体

            // 设置对话框中的所有组件使用相同的字体
            Font font = new Font("Arial", Font.PLAIN, 15);
            for (Component component : panel.getComponents()) {
                if (component instanceof JComponent) {
                    ((JComponent) component).setFont(font);
                }
            }

            // 创建“杀死进程”按钮
            JButton killProcessButton = new JButton("Kill Process"); // 修改按钮文本
            killProcessButton.setFont(new Font("Arial", Font.PLAIN, 15)); // 统一字体大小
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
            JButton viewProgramButton = new JButton("OK"); // 修改按钮文本
            viewProgramButton.setFont(new Font("Arial", Font.PLAIN, 15)); // 统一字体大小
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
        // java PortLookupUtil
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new PortLookupUtil();
            }
        });
    }
}