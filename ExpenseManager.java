import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.*;
import java.nio.file.*;
import java.net.URLDecoder;
import java.sql.*;
import java.util.*;

public class ExpenseManager {

    static String webPath = "web";

    public static void main(String[] args) throws Exception {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    description VARCHAR(200) NOT NULL,
                    amount DECIMAL(10,2) NOT NULL,
                    category VARCHAR(60) NOT NULL,
                    expense_date DATE NOT NULL
                )
            """);
        }

        HttpServer server = HttpServer.create(
            new InetSocketAddress(8080), 0);

        server.createContext("/api/expenses", exchange -> {
            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type");

            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                String method = exchange.getRequestMethod();

                if (method.equals("GET")) {
                    sendJson(exchange, getExpenses());
                } else if (method.equals("POST")) {
                    String body = new String(
                        exchange.getRequestBody().readAllBytes());
                    Map<String, String> data = parse(body);

                    String description = data.get("description");
                    double amount = Double.parseDouble(data.get("amount"));
                    String date = data.get("date");
                    String category = categorize(description);

                    try (Connection con = Database.getConnection();
                         PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO expenses(description,amount,category,expense_date) VALUES(?,?,?,?)")) {
                        ps.setString(1, description);
                        ps.setDouble(2, amount);
                        ps.setString(3, category);
                        ps.setDate(4, java.sql.Date.valueOf(date));
                        ps.executeUpdate();
                    }

                    sendJson(exchange, "{\"message\":\"Expense saved\"}");
                } else if (method.equals("DELETE")) {
                    String id = exchange.getRequestURI()
                        .getQuery().replace("id=", "");
                    try (Connection con = Database.getConnection();
                         PreparedStatement ps = con.prepareStatement(
                            "DELETE FROM expenses WHERE id=?")) {
                        ps.setInt(1, Integer.parseInt(id));
                        ps.executeUpdate();
                    }
                    sendJson(exchange, "{\"message\":\"Expense deleted\"}");
                } else {
                    sendJson(exchange, "{\"error\":\"Unsupported method\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                exchange.getResponseHeaders().set(
                    "Content-Type", "application/json");
                sendJson(exchange,
                    "{\"error\":\"Request failed. Check Java terminal.\"}");
            }
        });

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            Path file = Paths.get(webPath, path.substring(1)).normalize();

            if (!file.startsWith(Paths.get(webPath).toAbsolutePath().normalize())
                && file.isAbsolute()) {
                exchange.sendResponseHeaders(403, -1);
                return;
            }

            if (!Files.exists(file) || Files.isDirectory(file)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String type = path.endsWith(".css") ? "text/css"
                : path.endsWith(".js") ? "application/javascript"
                : "text/html";

            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type",
                type + "; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        server.start();
        System.out.println("Expense Manager running at http://localhost:8080");
    }

    static String categorize(String description) {
        String d = description.toLowerCase();
        if (d.matches(".*(food|lunch|dinner|restaurant|grocery|coffee).*"))
            return "Food";
        if (d.matches(".*(bus|train|taxi|uber|fuel|petrol|travel).*"))
            return "Transport";
        if (d.matches(".*(bill|electricity|water|rent|recharge).*"))
            return "Bills";
        if (d.matches(".*(shirt|shopping|shoes|clothes).*"))
            return "Shopping";
        return "Other";
    }

    static String getExpenses() throws SQLException {
        StringBuilder json = new StringBuilder("[");
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                "SELECT * FROM expenses ORDER BY expense_date DESC, id DESC")) {
            while (rs.next()) {
                if (json.length() > 1) json.append(",");
                json.append("{\"id\":").append(rs.getInt("id"))
                    .append(",\"description\":\"")
                    .append(escape(rs.getString("description")))
                    .append("\",\"amount\":")
                    .append(rs.getBigDecimal("amount"))
                    .append(",\"category\":\"")
                    .append(escape(rs.getString("category")))
                    .append("\",\"date\":\"")
                    .append(rs.getDate("expense_date")).append("\"}");
            }
        }
        return json.append("]").toString();
    }

    static Map<String, String> parse(String body) throws Exception {
        Map<String, String> map = new HashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "\"(description|amount|date)\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9.]+))"
        ).matcher(body);
        while (m.find())
            map.put(m.group(1), m.group(2) != null ? m.group(2) : m.group(3));
        return map;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ");
    }

    static void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
