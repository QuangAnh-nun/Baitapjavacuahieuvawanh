package giaodien;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * GiaoDien.java
 * Demo offline (không dùng SQL) cho "Ứng dụng Quản lý Sự kiện & Vé"
 *
 * - File: Source Packages/giaodien/GiaoDien.java
 * - Chạy trực tiếp trong NetBeans / IntelliJ / Eclipse
 *
 * Tính năng:
 * - Quản lý Sự kiện: tạo, chỉnh sửa, xóa (Tên, Ngày, Địa điểm, Tổng vé)
 * - Quản lý Vé: đặt vé, chỉnh sửa, hủy vé; khi đặt giảm vé còn lại; khi hủy tăng lại
 * - Tra cứu vé theo mã
 * - Export CSV danh sách vé
 * - Biểu đồ tròn tỉ lệ vé đã bán theo hạng (Thường/Vip/Vvip)
 *
 * Không cần kết nối database — dữ liệu lưu trong ArrayList.
 */
public class GiaoDien extends JFrame {

    // --- Data models (in-memory) ---
    private final List<Event> events = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();

    // --- UI components ---
    private DefaultTableModel modelEvents;
    private DefaultTableModel modelTickets;
    private JTable tableEvents;
    private JTable tableTickets;
    private JTextField txtSearch;
    private PieChartPanel pieChartPanel;

    // Fonts
    private final Font FONT_TITLE = new Font("Cascadia Mono", Font.BOLD, 22);
    private final Font FONT_NORMAL = new Font("Cascadia Mono", Font.PLAIN, 13);

    public GiaoDien() {
        setTitle("Ứng dụng Quản lý Sự kiện & Vé (Demo offline)");
        setSize(1100, 780);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        initSampleData();
        initUI();
        refreshAll();
    }

    private void initSampleData() {
        // create some sample events
        events.add(new Event("Music Concert", LocalDate.of(2025,11,18), "Sân vận động Mỹ Đình", 200));
        events.add(new Event("Múa rối nước", LocalDate.of(2025,12,25), "Nhà hát múa rối nước", 150));
        events.add(new Event("Hội chợ Tết", LocalDate.of(2026,1,31), "Quảng trường Hồ Tây", 300));
        events.add(new Event("Festival Ánh Sáng", LocalDate.of(2026,2,10), "Phố đi bộ Nguyễn Huệ", 180));
        events.add(new Event("Triển lãm Công nghệ", LocalDate.of(2026,3,20), "Trung tâm Hội nghị Quốc gia", 250));

        // create some sample tickets (assign to events)
        tickets.add(new Ticket("Nguyễn Mạnh Hiếu", "Vip", events.get(2).id));
        tickets.add(new Ticket("Đỗ Quang Anh", "Vvip", events.get(0).id));
        tickets.add(new Ticket("Lê Thị Minh Lý", "Thường", events.get(1).id));
        tickets.add(new Ticket("Trần Minh Khoa", "Vip", events.get(3).id));
        tickets.add(new Ticket("Phạm Nhật Huy", "Vvip", events.get(4).id));

        // reduce event remaining by those placed
        for (Ticket t : tickets) {
            Event e = findEventById(t.eventId);
            if (e != null) e.decreaseOne();
        }
    }

    private void initUI() {
        getContentPane().setBackground(new Color(245,245,247));
        setLayout(new BorderLayout(10,10));

        // header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(75,80,90));
        header.setBorder(new EmptyBorder(16,20,16,20));
        JLabel lblTitle = new JLabel("Ứng dụng Quản lý Sự kiện & Vé");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(Color.WHITE);
        header.add(lblTitle, BorderLayout.WEST);

        JButton btnCreateEvent = createPrimaryButton("+ Tạo sự kiện");
        btnCreateEvent.addActionListener(e -> openEventDialog(null));
        header.add(btnCreateEvent, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // center split left (tables) and right (chart + stats)
        JPanel center = new JPanel(new BorderLayout());
        center.setBorder(new EmptyBorder(12,12,12,12));
        center.setBackground(new Color(245,245,247));
        add(center, BorderLayout.CENTER);

        // ----- Events table -----
        modelEvents = new DefaultTableModel(new Object[]{"Mã", "Sự kiện", "Ngày", "Địa điểm", "Tổng vé", "Còn lại"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        tableEvents = new JTable(modelEvents);
        styleTable(tableEvents);
        tableEvents.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane spEvents = new JScrollPane(tableEvents);
        spEvents.setBorder(BorderFactory.createTitledBorder("Danh sách Sự kiện"));
        spEvents.setPreferredSize(new Dimension(700, 220));

        // popup menu for events
        JPopupMenu popupEvent = new JPopupMenu();
        JMenuItem miEventEdit = new JMenuItem("Chỉnh sửa");
        JMenuItem miEventDelete = new JMenuItem("Xóa");
        JMenuItem miEventViewTickets = new JMenuItem("Xem vé sự kiện");
        popupEvent.add(miEventEdit);
        popupEvent.add(miEventDelete);
        popupEvent.addSeparator();
        popupEvent.add(miEventViewTickets);

        tableEvents.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int r = tableEvents.rowAtPoint(e.getPoint());
                    if (r >= 0) {
                        tableEvents.setRowSelectionInterval(r, r);
                        popupEvent.show(tableEvents, e.getX(), e.getY());
                    }
                }
            }
        });

        miEventEdit.addActionListener(e -> {
            int r = tableEvents.getSelectedRow();
            if (r >= 0) {
                int id = Integer.parseInt(modelEvents.getValueAt(r,0).toString());
                openEventDialog(findEventById(id));
            }
        });

        miEventDelete.addActionListener(e -> {
            int r = tableEvents.getSelectedRow();
            if (r >= 0) {
                int id = Integer.parseInt(modelEvents.getValueAt(r,0).toString());
                int confirm = JOptionPane.showConfirmDialog(this, "Xóa sự kiện sẽ xóa cả vé liên quan. Tiếp tục?", "Xác nhận", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    deleteEvent(id);
                }
            }
        });

        miEventViewTickets.addActionListener(e -> {
            int r = tableEvents.getSelectedRow();
            if (r >= 0) {
                int id = Integer.parseInt(modelEvents.getValueAt(r,0).toString());
                filterTicketsByEvent(id);
            }
        });

        // ----- Actions (search/book/export) -----
        JPanel pnlActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        pnlActions.setBackground(new Color(245,245,247));
        txtSearch = new JTextField("Tra cứu mã vé...", 18);
        txtSearch.setFont(FONT_NORMAL);
        txtSearch.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (txtSearch.getText().trim().equals("Tra cứu mã vé...")) txtSearch.setText("");
            }
            @Override public void focusLost(FocusEvent e) {
                if (txtSearch.getText().trim().isEmpty()) txtSearch.setText("Tra cứu mã vé...");
            }
        });

        JButton btnSearch = createAccentButton("Tra cứu");
        btnSearch.addActionListener(e -> searchTicket());

        JButton btnBook = createAccentButton("Đặt vé / Thanh toán");
        btnBook.addActionListener(e -> openTicketDialog(null));

        JButton btnExport = createAccentButton("Export CSV");
        btnExport.addActionListener(e -> exportTicketsCsv());

        pnlActions.add(txtSearch);
        pnlActions.add(btnSearch);
        pnlActions.add(btnBook);
        pnlActions.add(btnExport);

        // ----- Tickets table -----
        modelTickets = new DefaultTableModel(new Object[]{"Mã vé", "Họ tên", "Hạng vé", "Mã SK", "Sự kiện", "Trạng thái", "Ngày đặt"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableTickets = new JTable(modelTickets);
        styleTable(tableTickets);
        JScrollPane spTickets = new JScrollPane(tableTickets);
        spTickets.setBorder(BorderFactory.createTitledBorder("Danh sách Vé"));
        spTickets.setPreferredSize(new Dimension(700, 280));

        // popup for tickets
        JPopupMenu popupTicket = new JPopupMenu();
        JMenuItem miTicketEdit = new JMenuItem("Chỉnh sửa vé");
        JMenuItem miTicketCancel = new JMenuItem("Hủy vé");
        popupTicket.add(miTicketEdit);
        popupTicket.add(miTicketCancel);

        tableTickets.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int r = tableTickets.rowAtPoint(e.getPoint());
                    if (r >= 0) {
                        tableTickets.setRowSelectionInterval(r, r);
                        popupTicket.show(tableTickets, e.getX(), e.getY());
                    }
                }
            }
        });

        miTicketEdit.addActionListener(e -> {
            int r = tableTickets.getSelectedRow();
            if (r >= 0) {
                int id = Integer.parseInt(modelTickets.getValueAt(r,0).toString());
                openTicketDialog(findTicketById(id));
            }
        });

        miTicketCancel.addActionListener(e -> {
            int r = tableTickets.getSelectedRow();
            if (r >= 0) {
                int id = Integer.parseInt(modelTickets.getValueAt(r,0).toString());
                cancelTicket(id);
            }
        });

        // Left column (events + actions + tickets)
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(new Color(245,245,247));
        left.add(spEvents);
        left.add(Box.createVerticalStrut(8));
        left.add(pnlActions);
        left.add(Box.createVerticalStrut(6));
        left.add(spTickets);

        center.add(left, BorderLayout.CENTER);

        // ----- Right column: pie chart + stats -----
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setPreferredSize(new Dimension(360, 0));
        right.setBackground(new Color(245,245,247));
        right.setBorder(new EmptyBorder(6,6,6,6));

        pieChartPanel = new PieChartPanel();
        pieChartPanel.setPreferredSize(new Dimension(340,320));
        pieChartPanel.setBorder(BorderFactory.createTitledBorder("Tỉ lệ vé đã bán theo hạng"));
        right.add(pieChartPanel);

        right.add(Box.createVerticalStrut(12));
        JPanel statsPanel = new JPanel(new GridLayout(3,1,6,6));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Thống kê nhanh"));
        statsPanel.setBackground(new Color(245,245,247));

        JLabel lblEvents = new JLabel();
        JLabel lblTotalTickets = new JLabel();
        JLabel lblSoldTickets = new JLabel();
        lblEvents.setFont(FONT_NORMAL);
        lblTotalTickets.setFont(FONT_NORMAL);
        lblSoldTickets.setFont(FONT_NORMAL);
        statsPanel.add(lblEvents);
        statsPanel.add(lblTotalTickets);
        statsPanel.add(lblSoldTickets);

        right.add(statsPanel);

        center.add(right, BorderLayout.EAST);

        // bottom toolbar
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setBackground(new Color(245,245,247));
        JButton btnRefresh = createPrimaryButton("Làm mới");
        btnRefresh.addActionListener(e -> {
            refreshAll();
        });
        bottom.add(btnRefresh);
        add(bottom, BorderLayout.SOUTH);

        // update stats initially
        SwingUtilities.invokeLater(() -> {
            lblEvents.setText("Số sự kiện: " + events.size());
            lblTotalTickets.setText("Tổng vé hệ thống: " + (tickets.size() + events.stream().mapToInt(ev -> ev.soldCount()).sum()));
            lblSoldTickets.setText("Vé đã bán: " + tickets.stream().filter(t -> !"Hủy".equalsIgnoreCase(t.status)).count());
        });
    }

    // ---------- Business logic ----------
    private void refreshAll() {
        refreshEventTable();
        refreshTicketTable();
        pieChartPanel.updateData(computeCounts());
        repaint();
    }

    private void refreshEventTable() {
        modelEvents.setRowCount(0);
        events.sort(Comparator.comparing(ev -> ev.date));
        for (Event e : events) {
            modelEvents.addRow(new Object[]{e.id, e.name, e.date.toString(), e.location, e.totalTickets, e.remainingTickets});
        }
    }

    private void refreshTicketTable() {
        modelTickets.setRowCount(0);
        tickets.stream()
                .sorted((a,b) -> Integer.compare(b.id, a.id))
                .forEach(t -> {
                    Event ev = findEventById(t.eventId);
                    String evName = ev == null ? "(đã xóa)" : ev.name;
                    modelTickets.addRow(new Object[]{
                            t.id, t.holderName, t.seatClass, t.eventId, evName, t.status, t.bookedAt
                    });
                });
    }

    private Event findEventById(int id) {
        return events.stream().filter(e -> e.id == id).findFirst().orElse(null);
    }

    private Ticket findTicketById(int id) {
        return tickets.stream().filter(t -> t.id == id).findFirst().orElse(null);
    }

    private void deleteEvent(int id) {
        // remove tickets related
        tickets.removeIf(t -> t.eventId == id);
        events.removeIf(e -> e.id == id);
        refreshAll();
        JOptionPane.showMessageDialog(this, "Đã xóa sự kiện và vé liên quan.");
    }

    private void cancelTicket(int ticketId) {
        Ticket t = findTicketById(ticketId);
        if (t == null) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy vé.");
            return;
        }
        if ("Hủy".equalsIgnoreCase(t.status)) {
            JOptionPane.showMessageDialog(this, "Vé đã ở trạng thái Hủy.");
            return;
        }
        t.status = "Hủy";
        // tăng lại event remaining
        Event ev = findEventById(t.eventId);
        if (ev != null) ev.increaseOne();
        refreshAll();
        JOptionPane.showMessageDialog(this, "Đã hủy vé.");
    }

    private void filterTicketsByEvent(int eventId) {
        modelTickets.setRowCount(0);
        tickets.stream()
                .filter(t -> t.eventId == eventId)
                .sorted((a,b) -> Integer.compare(b.id, a.id))
                .forEach(t -> {
                    Event ev = findEventById(t.eventId);
                    String evName = ev == null ? "(đã xóa)" : ev.name;
                    modelTickets.addRow(new Object[]{
                            t.id, t.holderName, t.seatClass, t.eventId, evName, t.status, t.bookedAt
                    });
                });
    }

    private void searchTicket() {
        String key = txtSearch.getText().trim();
        if (key.isEmpty() || key.equals("Tra cứu mã vé...")) {
            JOptionPane.showMessageDialog(this, "Nhập mã vé để tra cứu.");
            return;
        }
        try {
            int id = Integer.parseInt(key);
            Ticket t = findTicketById(id);
            if (t == null) {
                JOptionPane.showMessageDialog(this, "Không tìm thấy vé với mã " + id);
                return;
            }
            modelTickets.setRowCount(0);
            Event ev = findEventById(t.eventId);
            String evName = ev == null ? "(đã xóa)" : ev.name;
            modelTickets.addRow(new Object[]{t.id, t.holderName, t.seatClass, t.eventId, evName, t.status, t.bookedAt});
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Mã vé phải là số nguyên.");
        }
    }

    private void exportTicketsCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV file", "csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".csv")) path += ".csv";
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
                bw.write("MaVe,HoTen,HangVe,MaSuKien,TenSuKien,TrangThai,NgayDat\n");
                for (Ticket t : tickets) {
                    Event ev = findEventById(t.eventId);
                    String evName = ev == null ? "" : ev.name;
                    bw.write(String.format("%d,%s,%s,%d,%s,%s,%s\n",
                            t.id,
                            escapeCsv(t.holderName),
                            t.seatClass,
                            t.eventId,
                            escapeCsv(evName),
                            t.status,
                            t.bookedAt
                    ));
                }
                JOptionPane.showMessageDialog(this, "Export CSV thành công: " + path);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Lỗi export: " + ex.getMessage());
            }
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---------- Dialogs ----------
    private void openEventDialog(Event editing) {
        JDialog dialog = new JDialog(this, editing == null ? "Tạo Sự kiện" : "Chỉnh sửa Sự kiện", true);
        dialog.setSize(420, 320);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(5,2,8,8));
        form.setBorder(new EmptyBorder(12,12,12,12));
        JTextField txtName = new JTextField();
        JTextField txtDate = new JTextField("YYYY-MM-DD");
        JTextField txtLocation = new JTextField();
        JTextField txtTotal = new JTextField("0");

        form.add(new JLabel("Tên sự kiện:"));
        form.add(txtName);
        form.add(new JLabel("Ngày tổ chức:"));
        form.add(txtDate);
        form.add(new JLabel("Địa điểm:"));
        form.add(txtLocation);
        form.add(new JLabel("Tổng vé:"));
        form.add(txtTotal);

        dialog.add(form, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = createPrimaryButton("Hủy");
        JButton btnSave = createPrimaryButton("Lưu");
        foot.add(btnCancel);
        foot.add(btnSave);
        dialog.add(foot, BorderLayout.SOUTH);

        if (editing != null) {
            txtName.setText(editing.name);
            txtDate.setText(editing.date.toString());
            txtLocation.setText(editing.location);
            txtTotal.setText(Integer.toString(editing.totalTickets));
        }

        btnCancel.addActionListener(e -> dialog.dispose());
        btnSave.addActionListener(e -> {
            String name = txtName.getText().trim();
            String dateS = txtDate.getText().trim();
            String loc = txtLocation.getText().trim();
            int total;
            if (name.isEmpty() || dateS.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập tên và ngày.");
                return;
            }
            try {
                total = Integer.parseInt(txtTotal.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Tổng vé phải là số nguyên.");
                return;
            }
            try {
                LocalDate date = LocalDate.parse(dateS);
                if (editing == null) {
                    events.add(new Event(name, date, loc, total));
                    JOptionPane.showMessageDialog(dialog, "Tạo sự kiện thành công.");
                } else {
                    // chỉnh sửa: nếu tổng vé giảm nhỏ hơn số đã bán => báo lỗi
                    int sold = editing.soldCount();
                    if (total < sold) {
                        JOptionPane.showMessageDialog(dialog, "Không thể đặt Tổng vé < số vé đã bán ("+sold+").");
                        return;
                    }
                    editing.name = name;
                    editing.date = date;
                    editing.location = loc;
                    // điều chỉnh remainingTickets theo tổng mới
                    int diff = total - editing.totalTickets;
                    editing.totalTickets = total;
                    editing.remainingTickets += diff; // diff có thể âm hoặc dương
                    if (editing.remainingTickets < 0) editing.remainingTickets = 0;
                    JOptionPane.showMessageDialog(dialog, "Cập nhật sự kiện thành công.");
                }
                dialog.dispose();
                refreshAll();
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(dialog, "Ngày không hợp lệ. Dùng mẫu YYYY-MM-DD.");
            }
        });

        dialog.setVisible(true);
    }

    private void openTicketDialog(Ticket editing) {
        JDialog dialog = new JDialog(this, editing == null ? "Đặt vé" : "Chỉnh sửa vé", true);
        dialog.setSize(420, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(6,2,8,8));
        form.setBorder(new EmptyBorder(12,12,12,12));
        JTextField txtName = new JTextField();
        JComboBox<String> cboClass = new JComboBox<>(new String[]{"Thường","Vip","Vvip"});
        JComboBox<EventItem> cboEvent = new JComboBox<>();
        for (Event e : events) cboEvent.addItem(new EventItem(e.id, e.name));

        form.add(new JLabel("Họ tên:"));
        form.add(txtName);
        form.add(new JLabel("Hạng vé:"));
        form.add(cboClass);
        form.add(new JLabel("Sự kiện:"));
        form.add(cboEvent);

        dialog.add(form, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = createPrimaryButton("Hủy");
        JButton btnSave = createPrimaryButton("Lưu");
        foot.add(btnCancel);
        foot.add(btnSave);
        dialog.add(foot, BorderLayout.SOUTH);

        if (editing != null) {
            txtName.setText(editing.holderName);
            cboClass.setSelectedItem(editing.seatClass);
            // select event item
            for (int i=0;i<cboEvent.getItemCount();i++) {
                if (cboEvent.getItemAt(i).id == editing.eventId) { cboEvent.setSelectedIndex(i); break; }
            }
        }

        btnCancel.addActionListener(e -> dialog.dispose());
        btnSave.addActionListener(e -> {
            String name = txtName.getText().trim();
            String seatClass = (String) cboClass.getSelectedItem();
            EventItem evItem = (EventItem) cboEvent.getSelectedItem();
            if (name.isEmpty() || evItem == null) {
                JOptionPane.showMessageDialog(dialog, "Nhập đầy đủ họ tên và chọn sự kiện.");
                return;
            }
            Event ev = findEventById(evItem.id);
            if (ev == null) { JOptionPane.showMessageDialog(dialog, "Sự kiện không tồn tại."); return; }

            if (editing == null) {
                if (ev.remainingTickets <= 0) {
                    JOptionPane.showMessageDialog(dialog, "Sự kiện này đã hết vé.");
                    return;
                }
                Ticket t = new Ticket(name, seatClass, ev.id);
                tickets.add(t);
                ev.decreaseOne();
                JOptionPane.showMessageDialog(dialog, "Đặt vé thành công (Mã vé: " + t.id + ")");
            } else {
                // chỉnh sửa (chỉ thay tên/hạng)
                editing.holderName = name;
                editing.seatClass = seatClass;
                JOptionPane.showMessageDialog(dialog, "Cập nhật vé thành công.");
            }
            dialog.dispose();
            refreshAll();
        });

        dialog.setVisible(true);
    }

    // ---------- Pie chart data ----------
    private Counts computeCounts() {
        int thuong = 0, vip = 0, vvip = 0;
        for (Ticket t : tickets) {
            if ("Hủy".equalsIgnoreCase(t.status)) continue;
            String s = t.seatClass.toLowerCase();
            if (s.contains("v")) { // vip/vvip detection
                if (s.contains("vv")) vvip++;
                else vip++;
            } else {
                thuong++;
            }
        }
        return new Counts(thuong, vip, vvip);
    }

    // ---------- UI helpers ----------
    private JButton createPrimaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(220,225,230));
        b.setFont(FONT_NORMAL);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(200,210,220)); }
            public void mouseExited(MouseEvent e) { b.setBackground(new Color(220,225,230)); }
        });
        return b;
    }

    private JButton createAccentButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(160,240,220));
        b.setFont(FONT_NORMAL);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8,12,8,12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(140,225,200)); }
            public void mouseExited(MouseEvent e) { b.setBackground(new Color(160,240,220)); }
        });
        return b;
    }

    private void styleTable(JTable t) {
        t.setFont(FONT_NORMAL);
        t.setRowHeight(26);
        t.getTableHeader().setFont(new Font("Cascadia Mono", Font.BOLD, 13));
        t.getTableHeader().setBackground(new Color(230,230,230));
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(220,220,220));
    }

    // ---------- Utility classes ----------
    private static class Event {
        private static int NEXT_ID = 1;
        int id;
        String name;
        LocalDate date;
        String location;
        int totalTickets;
        int remainingTickets;

        Event(String name, LocalDate date, String location, int totalTickets) {
            this.id = NEXT_ID++;
            this.name = name;
            this.date = date;
            this.location = location;
            this.totalTickets = Math.max(0, totalTickets);
            this.remainingTickets = this.totalTickets;
        }

        void decreaseOne() { if (remainingTickets > 0) remainingTickets--; }
        void increaseOne() { remainingTickets++; if (remainingTickets > totalTickets) remainingTickets = totalTickets; }
        int soldCount() { return totalTickets - remainingTickets; }
    }

    private static class Ticket {
        private static int NEXT_ID = 1000;
        int id;
        String holderName;
        String seatClass;
        int eventId;
        String status; // "Đã đặt" or "Hủy"
        String bookedAt;

        Ticket(String holderName, String seatClass, int eventId) {
            this.id = NEXT_ID++;
            this.holderName = holderName;
            this.seatClass = seatClass;
            this.eventId = eventId;
            this.status = "Đã đặt";
            this.bookedAt = java.time.LocalDateTime.now().toString();
        }
    }

    private static class EventItem {
        int id; String name;
        EventItem(int id, String name) { this.id = id; this.name = name; }
        public String toString() { return name + " (#" + id + ")"; }
    }

    private static class Counts {
        int thuong, vip, vvip;
        Counts(int t,int v,int vv) { thuong=t; vip=v; vvip=vv; }
    }

    // ---------- Pie chart panel (custom) ----------
    private static class PieChartPanel extends JPanel {
        private Counts data = new Counts(1,1,1); // default dummy to avoid div0

        void updateData(Counts c) {
            if (c == null) c = new Counts(0,0,0);
            // ensure at least one slice drawn if all zero
            if (c.thuong + c.vip + c.vvip == 0) { data = new Counts(1,0,0); }
            else data = c;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            int size = Math.min(w, h) - 40;
            int x = (w - size) / 2;
            int y = 20;
            double total = data.thuong + data.vip + data.vvip;
            double angle0 = 0;
            // slice: Thường (blue), Vip (green), Vvip (orange)
            Color[] colors = new Color[]{new Color(100,160,240), new Color(100,200,150), new Color(255,170,90)};
            int[] vals = new int[]{data.thuong, data.vip, data.vvip};
            for (int i=0;i<3;i++) {
                double portion = vals[i] / total;
                int angle = (int)Math.round(portion * 360);
                g2.setColor(colors[i]);
                g2.fillArc(x, y, size, size, (int)Math.round(angle0), angle);
                angle0 += angle;
            }
            // legend
            int lx = 20;
            int ly = y + size + 10;
            g2.setFont(new Font("Cascadia Mono", Font.PLAIN, 12));
            g2.setColor(Color.BLACK);
            g2.drawString("Thường: " + data.thuong, lx, ly + 12);
            g2.setColor(colors[0]);
            g2.fillRect(lx-18, ly, 12, 12);

            g2.setColor(Color.BLACK);
            g2.drawString("Vip: " + data.vip, lx, ly + 12 + 20);
            g2.setColor(colors[1]);
            g2.fillRect(lx-18, ly+20, 12, 12);

            g2.setColor(Color.BLACK);
            g2.drawString("Vvip: " + data.vvip, lx, ly + 12 + 40);
            g2.setColor(colors[2]);
            g2.fillRect(lx-18, ly+40, 12, 12);

            g2.dispose();
        }
    }

    // ---------- Main ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new GiaoDien().setVisible(true);
        });
    }
}
