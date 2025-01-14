package se.yrgo.databaser;

import java.sql.*;
import java.util.*;
import java.util.logging.*;

import javax.sql.*;

import com.zaxxer.hikari.*;

public class App {
    private static final Logger logger = Logger.getLogger(App.class.getName());

    public static void main(String[] args) throws SQLException {
        // First we get hold of the credentials and create a datasource
        // A datasource is an object that will provide us with connections
        // to the database. In most cases it will also provide pooling, i.e.
        // it will keep a cache of connections and try to reuse them to
        // make things more performant.

        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        String host = System.getenv("DB_HOST");

        String url = String
                .format("jdbc:sqlserver://%s;encrypt=true;databaseName=Warehouse;trustServerCertificate=true", host);

        final DataSource ds = createDataSource(url, username, password);

        // This will populate the database with some data if there is none
        FakeData.generateData(ds);

        // Let's make a simple query to the database
        int count = findNumberOfProductsCostingMore(ds, 50);
        System.out.printf("There are %d products costing more than 50.%n", count);

        // And other one that is slightly more complex
        List<String> customers = findBestCustomers(ds);
        customers.forEach(System.out::println);

        // Let us also update some of the data
        increasePriceForScarceProducts(ds);
    }

    /**
     * Make a query to the database using the given datasource finding
     * which products that cost more than the given number.
     * 
     * @param ds   the datasource
     * @param cost a product cost
     * @return the number of products that cost more than the given cost
     * @throws SQLException
     */
    private static int findNumberOfProductsCostingMore(DataSource ds, double cost) throws SQLException {
        // In real-world system, we want to log what we do to be able to follow event
        // when someone reports a problem

        logger.fine(() -> "Finding products costing more than " + cost);

        // First we need to get at connection to the database
        // and it must be closed correctly, preferrably in a try-with-resource
        // If we don't close it we will eventually run out of connections
        // and our program will fail
        try (Connection conn = ds.getConnection()) {

            // Then we need an actual query. Using multiline strings this looks quite nice.
            // Dynamic data that we will fill in from variables are denoted with a ?

            String query = """
                    SELECT COUNT(*) FROM Product WHERE price > ? ;
                    """;

            // Now we create an actual statement that can be run on the database server
            // In Java we use the class PreparedStatement, that are created by the
            // server connection. This must also be closed correctly

            try (PreparedStatement stmt = conn.prepareStatement(query)) {

                // Now we fill in the blanks. For some reason they have
                // decided that in this instance we count from one :)
                // So here we set the first ? in the query to the value
                // of cost
                stmt.setDouble(1, cost);

                // Then we actually execute the query and send it to the
                // server. When we want something back we get that as
                // an ResultSet which contains all the rows of results.
                // In this case it will only contain one row and one column.
                ResultSet result = stmt.executeQuery();

                // ResultSets work like an iterator. It has a next() method
                // that returns true if we have another row to look at
                if (result.next()) {

                    // and now we can retrieve the only column from the set
                    // we can do this by position or by name
                    // in most cases by name is the better choice, but
                    // here we only have one cell.
                    // Again, we count from 1...
                    return result.getInt(1);
                }
            }

            // We should not get here
            throw new SQLException("Unable to retrieve count");
        }
    }

    /**
     * Find the ten best customers by how much they've spent
     * not considering their discount.
     * 
     * @param ds the datasource
     * @return the customers that have bought the most in terms of total price
     * @throws SQLException
     */
    private static List<String> findBestCustomers(DataSource ds) throws SQLException {
        logger.fine("Finding best customers");

        try (Connection conn = ds.getConnection()) {

            // the 10 customers who has payed the most,
            // not counting discounts
            String query = """
                    SELECT TOP(10) c.name
                      FROM OrderLine AS ol
                      JOIN CustomerOrder AS co
                        ON ol.orderId = co.orderId
                      JOIN Customer AS c
                        ON co.customerId = c.customerId
                     GROUP BY co.customerId, c.name
                     ORDER BY SUM(ol.quantity * ol.price) DESC;
                    """;

            // this try-with-resources can of course be joined with the one
            // above to make things a little less indented
            try (PreparedStatement stmt = conn.prepareStatement(query)) {

                // this time we do not have anything that is dynamic so
                // we just execute it
                ResultSet result = stmt.executeQuery();

                List<String> customers = new ArrayList<>();

                // Here we expect more than one row so
                // we need to loop over the result set
                while (result.next()) {
                    String name = result.getString("name");
                    customers.add(name);
                }

                return customers;
            }
        }
    }

    /**
     * Increase prices for all products that are less than 30% away
     * from their restock point. Increase the price with 10%.
     * 
     * @param ds the datasource
     * @throws SQLException
     */
    private static void increasePriceForScarceProducts(DataSource ds) throws SQLException {
        logger.fine("Increasing prices for scarce products");

        try (Connection conn = ds.getConnection()) {

            // Products that are less than 30% away from their
            // reorder point have their prices increased by 10%
            String query = """
                    UPDATE Product
                       SET price = price * 1.1  
                     WHERE stock < reorderPoint * 1.3;
                    """;

            try (PreparedStatement stmt = conn.prepareStatement(query)) {

                // Now we execute an update (update, insert, delete)
                // and might want to know how many rows we affected
                int rowCount = stmt.executeUpdate();

                logger.info(() -> "Updated prices of " + rowCount + " products");
            }
        }
    }

    /**
     * Create a DataSource using the HikariCP library.
     * 
     * @param jdbcUrl  the JDBC url to connect to
     * @param username the database user
     * @param password the database password
     * @return a datasource for the given database configuration
     */
    private static DataSource createDataSource(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }
}
