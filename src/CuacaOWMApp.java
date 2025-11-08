import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * Aplikasi Cek Cuaca – OpenWeatherMap (JDK 8, tanpa dependensi eksternal)
 * Versi "dipermak": UI manis, info lengkap, tanpa nge-freeze.
 */
public class CuacaOWMApp extends JFrame {

    // ====== API KEY OWM ======
    private static final String API_KEY = "aba534b094285303c6ce9f163f023a8f";

    // ====== PATH & CACHE ======
    private final Path FAVORITES = Paths.get("favorites.txt");
    private final Path ICON_CACHE_DIR = Paths.get(System.getProperty("user.home"), ".owm-cache");

    // ====== UI ======
    private final JComboBox<String> cbKota = new JComboBox<String>();
    private final JButton btnCek = new JButton("Cek");
    private final JButton btnFavorit = new JButton("★");
    private final JButton btnMuatCsv = new JButton("Muat CSV");
    private final JButton btnSimpanCsv = new JButton("Simpan CSV");

    private final JLabel lblIcon = new JLabel("", SwingConstants.CENTER);
    private final JLabel lblSuhu = new JLabel("--°C", SwingConstants.CENTER);
    private final JLabel lblKondisi = new JLabel("-", SwingConstants.CENTER);
    private final JLabel lblKota = new JLabel("-", SwingConstants.CENTER);

    private final JLabel lblHum = new JLabel("Kelembaban: - %", SwingConstants.CENTER);
    private final JLabel lblWind = new JLabel("Angin: - m/s (-)", SwingConstants.CENTER);
    private final JLabel lblFeels = new JLabel("Terasa: - °C", SwingConstants.CENTER);
    private final JLabel lblPressure = new JLabel("Tekanan: - hPa", SwingConstants.CENTER);
    private final JLabel lblVis = new JLabel("Visibilitas: - km", SwingConstants.CENTER);
    private final JLabel lblSun = new JLabel("Matahari: terbit - | terbenam -", SwingConstants.CENTER);
    private final JLabel lblCountry = new JLabel("-", SwingConstants.CENTER);

    private final JLabel status = new JLabel("Siap.");

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Kota","Negara","Kondisi","Suhu(°C)","Terasa(°C)","Kelembaban(%)","Tekanan(hPa)","Angin(m/s)","Arah","Vis(km)","Epoch"}, 0
    ) { @Override public boolean isCellEditable(int r,int c){ return false; } };
    private final JTable table = new JTable(model);

    // cache ringan (RAM) untuk hasil terbaru per kota
    private final Map<String, WeatherData> lastCache = new HashMap<String, WeatherData>();
    private final Map<String, Icon> iconMemoryCache = new HashMap<String, Icon>();

    public CuacaOWMApp() {
        super("Aplikasi Cek Cuaca – OWM (JDK8)");
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0,0));

        // ====== Toolbar Atas ======
        JPanel toolbar = new JPanel(new GridBagLayout());
        toolbar.setBorder(new EmptyBorder(10,12,10,12));
        toolbar.setBackground(new Color(248, 250, 252));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,6,6,6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 0;

        cbKota.setEditable(true);
        for (String s : Arrays.asList("Banjarmasin","Jakarta","Bandung","Surabaya","Yogyakarta","Denpasar","Medan","Makassar")) {
            cbKota.addItem(s);
        }
        loadFavoritesIntoCombo();

        JLabel lbl = new JLabel("Kota");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        g.gridx=0; g.weightx=0; toolbar.add(lbl, g);
        g.gridx=1; g.weightx=1; toolbar.add(cbKota, g);

        stylizeButton(btnCek);
        stylizeButton(btnFavorit);
        stylizeButton(btnMuatCsv);
        stylizeButton(btnSimpanCsv);

        g.gridx=2; g.weightx=0; toolbar.add(btnCek, g);
        g.gridx=3; toolbar.add(btnFavorit, g);
        g.gridx=4; toolbar.add(btnMuatCsv, g);
        g.gridx=5; toolbar.add(btnSimpanCsv, g);

        add(toolbar, BorderLayout.NORTH);

        // Enter di combobox langsung cek
        Component editor = cbKota.getEditor().getEditorComponent();
        editor.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode()==KeyEvent.VK_ENTER) btnCek.doClick();
            }
        });

        // ====== Tabel Tengah ======
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        centerNumberColumns(table, new int[]{3,4,5,6,7,9,10});
        JScrollPane spTable = new JScrollPane(table);

        // klik kanan: salin / hapus
        JPopupMenu pop = new JPopupMenu();
        JMenuItem miCopy = new JMenuItem("Salin baris (CSV)");
        JMenuItem miDel = new JMenuItem("Hapus baris");
        pop.add(miCopy); pop.add(miDel);
        table.setComponentPopupMenu(pop);
        miCopy.addActionListener(e -> copySelectedRowAsCsv());
        miDel.addActionListener(e -> deleteSelectedRow());

        // auto-resize
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {120,70,140,80,80,100,100,90,70,80,110};
        for (int i=0;i<widths.length;i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        // ====== Kartu Kanan ======
        JPanel card = buildInfoCard();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, spTable, card);
        split.setResizeWeight(0.66);
        add(split, BorderLayout.CENTER);

        // ====== Status Bar ======
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(6,12,6,12));
        statusBar.setBackground(new Color(245,245,245));
        status.setForeground(new Color(90,90,90));
        statusBar.add(status, BorderLayout.WEST);
        add(statusBar, BorderLayout.SOUTH);

        // ====== Event Wiring ======
        btnCek.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { cekCuacaDariCombo(); }
        });
        btnFavorit.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { simpanFavorit(); }
        });
        btnMuatCsv.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { muatCsv(); }
        });
        btnSimpanCsv.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { simpanCsv(); }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount()==2) {
                    int r = table.getSelectedRow();
                    if (r >= 0) {
                        String kota = String.valueOf(model.getValueAt(r,0));
                        WeatherData d = lastCache.get(kota);
                        if (d != null) applyToCard(d);
                        else {
                            String kondisi = String.valueOf(model.getValueAt(r,2));
                            lblKota.setText(kota);
                            lblKondisi.setText(kondisi);
                        }
                    }
                }
            }
        });

        // ====== Final Frame ======
        setMinimumSize(new Dimension(980, 560));
        setSize(1120, 640);
        setLocationRelativeTo(null);

        try { Files.createDirectories(ICON_CACHE_DIR); } catch (IOException ignored) {}
    }

    // ====== FAVORIT ======
    private void loadFavoritesIntoCombo() {
        try {
            if (!Files.exists(FAVORITES)) return;
            java.util.List<String> lines = Files.readAllLines(FAVORITES, StandardCharsets.UTF_8);
            for (String s : lines) {
                String k = s == null ? "" : s.trim();
                if (k.isEmpty()) continue;
                boolean exists = false;
                for (int i=0;i<cbKota.getItemCount();i++) {
                    if (k.equalsIgnoreCase(cbKota.getItemAt(i))) { exists = true; break; }
                }
                if (!exists) cbKota.addItem(k);
            }
        } catch (IOException ignored) {}
    }

    // ====== UI Helpers ======
    private void stylizeButton(JButton b) {
        b.setFocusPainted(false);
        b.setBackground(new Color(30,136,229));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(8,14,8,14));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(new Color(25,118,210)); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(new Color(30,136,229)); }
        });
    }

    private JPanel buildInfoCard() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(12,12,12,12));
        root.setBackground(Color.WHITE);

        JLabel hdr = new JLabel("Info Cuaca");
        hdr.setFont(hdr.getFont().deriveFont(Font.BOLD, 16f));
        hdr.setBorder(new EmptyBorder(0,0,8,0));
        root.add(hdr, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        lblKota.setFont(lblKota.getFont().deriveFont(Font.BOLD, 18f));
        lblSuhu.setFont(lblSuhu.getFont().deriveFont(Font.BOLD, 44f));
        lblKondisi.setFont(lblKondisi.getFont().deriveFont(Font.PLAIN, 16f));
        lblHum.setFont(lblHum.getFont().deriveFont(Font.PLAIN, 13f));
        lblWind.setFont(lblWind.getFont().deriveFont(Font.PLAIN, 13f));
        lblFeels.setFont(lblFeels.getFont().deriveFont(Font.PLAIN, 13f));
        lblPressure.setFont(lblPressure.getFont().deriveFont(Font.PLAIN, 13f));
        lblVis.setFont(lblVis.getFont().deriveFont(Font.PLAIN, 13f));
        lblSun.setFont(lblSun.getFont().deriveFont(Font.PLAIN, 13f));
        lblCountry.setFont(lblCountry.getFont().deriveFont(Font.BOLD, 12f));
        lblCountry.setForeground(new Color(120,120,120));

        lblIcon.setPreferredSize(new Dimension(150,150));
        lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        alignCenter(lblKota,lblCountry,lblSuhu,lblKondisi,lblHum,lblWind,lblFeels,lblPressure,lblVis,lblSun);

        center.add(lblIcon);
        center.add(Box.createVerticalStrut(6));
        center.add(lblKota);
        center.add(Box.createVerticalStrut(2));
        center.add(lblCountry);
        center.add(Box.createVerticalStrut(2));
        center.add(lblSuhu);
        center.add(Box.createVerticalStrut(2));
        center.add(lblKondisi);
        center.add(Box.createVerticalStrut(10));
        center.add(line());
        center.add(Box.createVerticalStrut(10));
        center.add(lblHum);
        center.add(Box.createVerticalStrut(4));
        center.add(lblWind);
        center.add(Box.createVerticalStrut(4));
        center.add(lblFeels);
        center.add(Box.createVerticalStrut(4));
        center.add(lblPressure);
        center.add(Box.createVerticalStrut(4));
        center.add(lblVis);
        center.add(Box.createVerticalStrut(4));
        center.add(lblSun);

        root.add(center, BorderLayout.CENTER);

        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1,1,1,1,new Color(230,230,230)),
                new EmptyBorder(12,12,12,12)
        ));
        return root;
    }

    private void alignCenter(JComponent... comps){
        for (JComponent c: comps) c.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private JComponent line() {
        JComponent p = new JComponent(){};
        p.setPreferredSize(new Dimension(1,1));
        p.setMinimumSize(new Dimension(1,1));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE,1));
        p.setBorder(BorderFactory.createMatteBorder(1,0,0,0, new Color(235,235,235)));
        p.setOpaque(false);
        return p;
    }

    private void centerNumberColumns(JTable t, int[] idx) {
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i : idx) t.getColumnModel().getColumn(i).setCellRenderer(center);
    }

    private void setBusy(final boolean busy, final String msg) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
                btnCek.setEnabled(!busy);
                setStatus(msg == null ? (busy ? "Memuat..." : "Siap.") : msg, busy ? new Color(120,120,120) : new Color(60,60,60));
            }
        });
    }

    private void setStatus(String msg, Color c){
        status.setText(msg);
        status.setForeground(c);
    }

    // ====== Actions ======
    private void cekCuacaDariCombo() {
        final String kota = String.valueOf(cbKota.getEditor().getItem()).trim();
        if (kota.isEmpty()) { JOptionPane.showMessageDialog(this,"Isi nama kota."); return; }

        setBusy(true, "Mengambil cuaca " + kota + "...");
        new SwingWorker<WeatherData, Void>() {
            @Override protected WeatherData doInBackground() throws Exception { return fetchWeatherOWM(kota); }
            @Override protected void done() {
                try {
                    WeatherData d = get();
                    lastCache.put(d.kota, d);
                    upsertRow(d);
                    applyToCard(d);
                    setStatus("Berhasil memuat " + d.kota + ".", new Color(0,128,0));
                } catch (Exception ex) {
                    setStatus("Gagal memuat: " + ex.getMessage(), new Color(180,0,0));
                    JOptionPane.showMessageDialog(CuacaOWMApp.this,"Gagal ambil cuaca: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void applyToCard(WeatherData d) {
        lblKota.setText(d.kota);
        lblCountry.setText(d.country==null||d.country.isEmpty()? "-" : d.country);
        lblSuhu.setText(fmt1(d.suhu) + "°C");
        lblKondisi.setText(d.kondisi);
        lblHum.setText("Kelembaban: " + d.kelembaban + " %");
        lblWind.setText("Angin: " + fmt2(d.angin) + " m/s (" + degToCompass(d.windDeg) + ")");
        lblFeels.setText("Terasa: " + fmt1(d.feelsLike) + " °C");
        lblPressure.setText("Tekanan: " + d.pressure + " hPa");
        lblVis.setText("Visibilitas: " + fmt1(d.visibilityKm) + " km");
        lblSun.setText("Matahari: terbit " + epochToClock(d.sunrise) + " | terbenam " + epochToClock(d.sunset));
        setIconForCondition(d.kota, d.kondisi, d.iconCode);
    }

    private void simpanFavorit() {
        String kota = String.valueOf(cbKota.getEditor().getItem()).trim();
        if (kota.isEmpty()) { JOptionPane.showMessageDialog(this,"Tidak ada kota untuk disimpan."); return; }
        boolean ada = false;
        for (int i=0;i<cbKota.getItemCount();i++) if (kota.equalsIgnoreCase(cbKota.getItemAt(i))) { ada = true; break; }
        if (!ada) cbKota.addItem(kota);

        try {
            LinkedHashSet<String> set = new LinkedHashSet<String>();
            if (Files.exists(FAVORITES)) set.addAll(Files.readAllLines(FAVORITES, StandardCharsets.UTF_8));
            set.add(kota);
            Files.write(FAVORITES, set, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Favorit tersimpan.");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Gagal simpan favorit: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void muatCsv() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
                model.setRowCount(0);
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.trim().isEmpty() || line.toLowerCase().startsWith("kota,")) continue;
                    String[] p = parseCsvLine(line);
                    if (p.length >= 11) model.addRow(new Object[]{p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],p[8],p[9],p[10]});
                }
                setStatus("CSV dimuat.", new Color(0,128,0));
            } catch (Exception ex) {
                setStatus("Gagal muat CSV: " + ex.getMessage(), new Color(180,0,0));
                JOptionPane.showMessageDialog(this, "Gagal muat CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void simpanCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("cuaca_owm.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8))) {
                w.write("Kota,Negara,Kondisi,Suhu(°C),Terasa(°C),Kelembaban(%),Tekanan(hPa),Angin(m/s),Arah,Vis(km),Epoch"); w.newLine();
                for (int i=0;i<model.getRowCount();i++) {
                    String[] row = new String[11];
                    for (int c=0;c<11;c++) row[c] = String.valueOf(model.getValueAt(i,c));
                    w.write(toCsv(row)); w.newLine();
                }
                setStatus("CSV tersimpan.", new Color(0,128,0));
            } catch (Exception ex) {
                setStatus("Gagal simpan CSV: " + ex.getMessage(), new Color(180,0,0));
                JOptionPane.showMessageDialog(this, "Gagal simpan CSV: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ====== Tabel helpers ======
    private void upsertRow(WeatherData d) {
        for (int i=0;i<model.getRowCount();i++) {
            if (String.valueOf(model.getValueAt(i,0)).equals(d.kota)) {
                model.setValueAt(d.country, i, 1);
                model.setValueAt(d.kondisi, i, 2);
                model.setValueAt(fmt1(d.suhu), i, 3);
                model.setValueAt(fmt1(d.feelsLike), i, 4);
                model.setValueAt(Integer.valueOf(d.kelembaban), i, 5);
                model.setValueAt(Integer.valueOf(d.pressure), i, 6);
                model.setValueAt(fmt2(d.angin), i, 7);
                model.setValueAt(degToCompass(d.windDeg), i, 8);
                model.setValueAt(fmt1(d.visibilityKm), i, 9);
                model.setValueAt(Long.valueOf(d.epoch), i, 10);
                return;
            }
        }
        model.addRow(new Object[]{
                d.kota, d.country, d.kondisi, fmt1(d.suhu), fmt1(d.feelsLike),
                Integer.valueOf(d.kelembaban), Integer.valueOf(d.pressure),
                fmt2(d.angin), degToCompass(d.windDeg), fmt1(d.visibilityKm), Long.valueOf(d.epoch)
        });
    }

    private void copySelectedRowAsCsv(){
        int r = table.getSelectedRow();
        if (r < 0) return;
        StringBuilder sb = new StringBuilder();
        for (int c=0;c<model.getColumnCount();c++){
            if (c>0) sb.append(',');
            String s = String.valueOf(model.getValueAt(r,c));
            if (s.indexOf(',')>=0 || s.indexOf('"')>=0) sb.append('"').append(s.replace("\"","\"\"")).append('"');
            else sb.append(s);
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
        setStatus("Baris disalin ke clipboard.", new Color(0,128,0));
    }

    private void deleteSelectedRow(){
        int r = table.getSelectedRow();
        if (r >= 0) model.removeRow(r);
    }

    private static String fmt1(double v){ return Double.isFinite(v)?String.format(java.util.Locale.US,"%.1f",v):"N/A"; }
    private static String fmt2(double v){ return Double.isFinite(v)?String.format(java.util.Locale.US,"%.2f",v):"N/A"; }

    private static String degToCompass(int deg){
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW"};
        int idx = (int)Math.round(((double)deg % 360) / 22.5) % 16;
        return dirs[idx];
    }

    private static String epochToClock(long epoch){
        if (epoch<=0) return "-";
        Date d = new Date(epoch*1000L);
        return new SimpleDateFormat("HH:mm").format(d);
        // Kalau mau WIB fix: SimpleDateFormat sdf=...; sdf.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
    }

    // ====== OWM fetch (HttpURLConnection, JDK8) ======
    private WeatherData fetchWeatherOWM(String kota) throws Exception {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" +
                URLEncoder.encode(kota, "UTF-8") +
                "&appid=" + API_KEY + "&units=metric&lang=id";

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept","application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        InputStream is = (code/100==2) ? conn.getInputStream() : conn.getErrorStream();
        String json = readAll(is);

        if (code/100 != 2) {
            if (code==401) throw new IOException("API key ditolak/limit (401).");
            if (code==404) throw new IOException("Kota tidak ditemukan (404).");
            throw new IOException("HTTP " + code + ": " + json);
        }

        String name = parseString(json, "\"name\":\"", "\"");
        if (name == null || name.isEmpty()) name = kota;

        String main = parseString(json, "\"main\":\"", "\""); // Clear, Clouds, dll
        if (main == null || main.isEmpty()) main = parseString(json, "\"description\":\"", "\"");
        String iconCode = parseString(json, "\"icon\":\"", "\"");

        double temp = parseDouble(json, "\"temp\":", Double.NaN);
        double feels = parseDouble(json, "\"feels_like\":", Double.NaN);
        int hum = parseInt(json, "\"humidity\":", 0);
        int pressure = parseInt(json, "\"pressure\":", 0);
        double wind = parseDouble(json, "\"speed\":", 0.0);
        int windDeg = parseInt(json, "\"deg\":", 0);
        long epoch = parseLong(json, "\"dt\":", 0);
        int vis = parseInt(json, "\"visibility\":", -1);
        String country = parseString(json, "\"country\":\"", "\"");
        long sunrise = parseLong(json, "\"sunrise\":", 0);
        long sunset  = parseLong(json, "\"sunset\":", 0);

        double visKm = vis > 0 ? (vis / 1000.0) : Double.NaN;

        return new WeatherData(
                capFirst(name),
                country==null?"":country,
                main==null||main.isEmpty()?"N/A":main,
                temp, feels, hum, pressure, wind, windDeg, visKm, epoch, iconCode, sunrise, sunset
        );
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
            return sb.toString();
        } finally { try { is.close(); } catch (IOException ignored) {} }
    }

    // ====== Ikon lokal + fallback OWM ======
    private void setIconForCondition(String kota, String kondisi, String iconCode) {
        try {
            String k = kondisi == null ? "" : kondisi.toLowerCase(java.util.Locale.ROOT);
            String file = null;
            if (k.contains("clear") || k.contains("cerah")) file = "/icons/clear.png";
            else if (k.contains("cloud")) file = "/icons/clouds.png";
            else if (k.contains("rain") || k.contains("drizzle") || k.contains("hujan")) file = "/icons/rain.png";
            else if (k.contains("thunder") || k.contains("badai")) file = "/icons/thunder.png";
            else if (k.contains("mist") || k.contains("fog") || k.contains("haze") || k.contains("kabut")) file = "/icons/mist.png";
            else if (k.contains("snow") || k.contains("salju")) file = "/icons/snow.png";

            Icon ico = null;
            if (file != null) {
                ico = loadIconResource(file, 140, 140);
            }
            if (ico == null) {
                ico = getIcon(iconCode);
            }

            if (ico != null) {
                lblIcon.setIcon(ico);
                lblIcon.setText("");
            } else {
                lblIcon.setIcon(null);
                lblIcon.setText(emojiFor(kondisi));
            }
            lblIcon.setToolTipText(kota + " – " + (kondisi == null ? "" : kondisi));
        } catch (Exception ex) {
            lblIcon.setIcon(null);
            lblIcon.setText(emojiFor(kondisi));
        }
    }

    private Icon loadIconResource(String path, int w, int h) {
        try {
            URL url = getClass().getResource(path);
            if (url == null) return null;
            ImageIcon raw = new ImageIcon(url);
            Image scaled = raw.getImage().getScaledInstance(w, h, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) { return null; }
    }

    private Icon getIcon(String iconCode) {
        if (iconCode == null || iconCode.trim().isEmpty()) return null;
        String key = "owm-" + iconCode;
        Icon mem = iconMemoryCache.get(key);
        if (mem != null) return mem;

        try {
            Files.createDirectories(ICON_CACHE_DIR);
            Path p = ICON_CACHE_DIR.resolve(iconCode + "@2x.png");
            if (!Files.exists(p)) {
                URL url = new URL("https://openweathermap.org/img/wn/" + iconCode + "@2x.png");
                try (InputStream in = url.openStream()) {
                    Files.copy(in, p, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            ImageIcon raw = new ImageIcon(p.toString());
            Image scaled = raw.getImage().getScaledInstance(140, 140, Image.SCALE_SMOOTH);
            Icon ico = new ImageIcon(scaled);
            iconMemoryCache.put(key, ico);
            return ico;
        } catch (Exception e) { return null; }
    }

    // ====== CSV ======
    private static String toCsv(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<cells.length;i++) {
            if (i>0) sb.append(',');
            String s = cells[i] == null ? "" : cells[i];
            if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) sb.append('"').append(s.replace("\"","\"\"")).append('"');
            else sb.append(s);
        }
        return sb.toString();
    }

    private static String[] parseCsvLine(String line) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i=0;i<line.length();i++) {
            char ch = line.charAt(i);
            if (inQ) {
                if (ch=='"' && i+1<line.length() && line.charAt(i+1)=='"') { cur.append('"'); i++; }
                else if (ch=='"') inQ=false;
                else cur.append(ch);
            } else {
                if (ch==',') { out.add(cur.toString()); cur.setLength(0); }
                else if (ch=='"') inQ=true;
                else cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    // ====== Mini parser JSON (sederhana) ======
    private static String parseString(String json, String start, String end) {
        int i = json.indexOf(start);
        if (i < 0) return "";
        i += start.length();
        int j = json.indexOf(end, i);
        if (j < 0) return "";
        return json.substring(i, j);
    }
    private static double parseDouble(String json, String key, double def) {
        int i = json.indexOf(key);
        if (i < 0) return def;
        i += key.length();
        int j = i;
        while (j < json.length() && "0123456789.-".indexOf(json.charAt(j)) >= 0) j++;
        try { return Double.parseDouble(json.substring(i, j)); } catch (Exception e) { return def; }
    }
    private static int parseInt(String json, String key, int def) {
        int i = json.indexOf(key);
        if (i < 0) return def;
        i += key.length();
        int j = i;
        while (j < json.length() && "0123456789-".indexOf(json.charAt(j)) >= 0) j++;
        try { return Integer.parseInt(json.substring(i, j)); } catch (Exception e) { return def; }
    }
    private static long parseLong(String json, String key, long def) {
        int i = json.indexOf(key);
        if (i < 0) return def;
        i += key.length();
        int j = i;
        while (j < json.length() && "0123456789".indexOf(json.charAt(j)) >= 0) j++;
        try { return Long.parseLong(json.substring(i, j)); } catch (Exception e) { return def; }
    }

    private static String capFirst(String s) { return (s==null||s.trim().isEmpty())?s:s.substring(0,1).toUpperCase()+s.substring(1); }
    private static String emojiFor(String cond) {
        String c = cond==null? "": cond.toLowerCase(java.util.Locale.ROOT);
        if (c.indexOf("cerah")>=0 || c.indexOf("clear")>=0) return "☀️";
        if (c.indexOf("awan")>=0 || c.indexOf("cloud")>=0) return "☁️";
        if (c.indexOf("hujan")>=0 || c.indexOf("rain")>=0 || c.indexOf("drizzle")>=0) return "🌧️";
        if (c.indexOf("badai")>=0 || c.indexOf("thunder")>=0) return "⛈️";
        if (c.indexOf("kabut")>=0 || c.indexOf("fog")>=0 || c.indexOf("mist")>=0 || c.indexOf("haze")>=0) return "🌫️";
        if (c.indexOf("salju")>=0 || c.indexOf("snow")>=0) return "❄️";
        return "🌡️";
    }

    // ====== Data holder ======
    static class WeatherData {
        final String kota, country, kondisi, iconCode;
        final double suhu, feelsLike, angin, visibilityKm;
        final int kelembaban, pressure, windDeg;
        final long epoch, sunrise, sunset;
        WeatherData(String kota, String country, String kondisi,
                    double suhu, double feelsLike, int kelembaban, int pressure,
                    double angin, int windDeg, double visibilityKm,
                    long epoch, String iconCode, long sunrise, long sunset) {
            this.kota=kota; this.country=country; this.kondisi=kondisi; this.iconCode=iconCode;
            this.suhu=suhu; this.feelsLike=feelsLike; this.kelembaban=kelembaban; this.pressure=pressure;
            this.angin=angin; this.windDeg=windDeg; this.visibilityKm=visibilityKm;
            this.epoch=epoch; this.sunrise=sunrise; this.sunset=sunset;
        }
    }

    // ====== main ======
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() { new CuacaOWMApp().setVisible(true); }
        });
    }
}
