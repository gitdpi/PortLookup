import javax.swing.*;
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
    private JList<String> resultList;
    private DefaultListModel<String> listModel;

    public PortLookupUtil() {
        initialize();
    }

    private void initialize() {
        frame = new JFrame("端口查找工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 500);
        frame.setLayout(new GridBagLayout()); // 使用 GridBagLayout 替换 BorderLayout

        // 设置窗口居中显示
        frame.setLocationRelativeTo(null);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 设置组件之间的间距

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());

        JLabel label = new JLabel("Port:");
        panel.add(label);

        portField = new JTextField(10);
        panel.add(portField);

        // 添加 ActionListener 以支持回车键触发搜索
        portField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchPort();
            }
        });

        JButton searchButton = new JButton("Search");
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
        frame.add(panel, gbc);

        listModel = new DefaultListModel<>();
        resultList = new JList<>(listModel);
        resultList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                    int index = resultList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        String selectedItem = resultList.getModel().getElementAt(index);
                        String[] parts = selectedItem.trim().split("\\s+");
                        if (parts.length > 4) {
                            String pid = parts[4]; // 确保正确提取 pid
                            System.out.println("Extracted PID for kill action: " + pid); // 添加调试信息

                            // 创建弹出菜单
                            JPopupMenu popupMenu = new JPopupMenu();
                            JMenuItem killProcessItem = new JMenuItem("杀死进程");
                            JMenuItem viewProgramItem = new JMenuItem("查看占用程序");

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

        listModel.clear();
        try {
            System.out.println("Executing command: netstat -ano | findstr :" + port);
            Process process = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr :" + port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 4) {
                    String pid = parts[4];
                    listModel.addElement(line);
                    System.out.println("Output line: " + line); 
                    System.out.println("Extracted PID: " + pid); // 添加调试信息
                }
            }
            System.out.println("Number of elements in listModel: " + listModel.getSize());
            System.out.println("JList updated with " + resultList.getModel().getSize() + " elements.");

            System.out.println("JList model set to listModel with " + resultList.getModel().getSize() + " elements."); // 添加调试信息
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
            JTextArea textArea = new JTextArea(output.toString());
            textArea.setEditable(false);
            textArea.setRows(10); // 设置行数
            textArea.setColumns(50); // 设置列数
            panel.add(new JScrollPane(textArea), BorderLayout.CENTER);

            // 创建按钮面板
            JPanel buttonPanel = new JPanel();
            buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

            // 创建对话框
            JOptionPane optionPane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{}, null);
            JDialog dialog = optionPane.createDialog(frame, "占用程序");

            // 添加“杀死进程”按钮
            JButton killProcessButton = new JButton("杀死进程");
            killProcessButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    endProcess(pid);
                    // 关闭对话框
                    dialog.dispose();
                }
            });
            buttonPanel.add(killProcessButton);

            // 添加“确定”按钮
            JButton okButton = new JButton("确定");
            okButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dialog.dispose();
                }
            });
            buttonPanel.add(okButton);

            // 将按钮面板添加到对话框
            dialog.add(buttonPanel, BorderLayout.SOUTH);

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