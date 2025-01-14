package se.yrgo.databaser;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.logging.*;

import javax.sql.*;

import net.datafaker.*;

public final class FakeData {
    private static final Logger logger = Logger.getLogger(FakeData.class.getName());

    record ProductPrice(int productId, double price) {}

    private FakeData() {
    }

    /**
     * If the database seems to be empty, generate lots of fake data
     * into Customer, Product, CustomerOrder and OrderLine.
     * 
     * @param ds the datasource to use
     */
    public static void generateData(DataSource ds) throws SQLException {
        try (Connection connection = ds.getConnection()) {
            if (isEmptyDatabase(connection)) {
                logger.info("Generating data into database");

                // using a fixed random generator will give us the very same
                // tables every time they are generated
                Random random = new Random(12345);
                Faker faker = new Faker(random);

                generateCustomers(connection, faker, random);
                generateProducts(connection, faker, random);
                generateOrders(connection, random);
            } else {
                logger.info("Data already present. No generation has been done.");
            }
        }
    }

    private static void generateCustomers(Connection connection, Faker faker, Random random) throws SQLException {

        logger.info("Generating customers");

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO Customer (name, address, zipCode, city, discount) VALUES (?, ?, ?, ?, ?);")) {
            for (int i = 0; i < 1000; i++) {
                String name = faker.name().fullName();
                String address = faker.address().streetName() + " "
                        + faker.expression("#{regexify '[1-9]?[1-9][A-D]?'}");
                String zipCode = faker.address().zipCode();
                String city = faker.address().cityName();
                double discount = random.nextDouble() < 0.3 ? 0.05 : 0.0;

                stmt.setString(1, name);
                stmt.setString(2, address);
                stmt.setString(3, zipCode);
                stmt.setString(4, city);
                stmt.setDouble(5, discount);

                stmt.addBatch();

                if (i % 50 == 0) {
                    stmt.executeBatch();
                }
            }

            stmt.executeBatch();
        }
    }

    private static void generateProducts(Connection connection, Faker faker, Random random) throws SQLException {

        logger.info("Generating products");

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO Product (productName, stock, reorderPoint, price) VALUES (?, ?, ?, ?);")) {
            for (int i = 0; i < 1000; i++) {
                String name = faker.commerce().productName();
                int stock = random.nextInt(100) + 10;
                int reorderPoint = random.nextInt(4 * (stock / 5));
                double price = random.nextDouble(10, 1000);

                stmt.setString(1, name);
                stmt.setInt(2, stock);
                stmt.setInt(3, reorderPoint);
                stmt.setDouble(4, price);

                stmt.addBatch();

                if (i % 50 == 0) {
                    stmt.executeBatch();
                }
            }

            stmt.executeBatch();
        }
    }

    private static void generateOrders(Connection connection, Random random) throws SQLException {

        logger.info("Generating orders");

        List<ProductPrice> productPrices = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT productId, price FROM Product;")) {
            ResultSet products = stmt.executeQuery();
            while (products.next()) {
                int productId = products.getInt("productId");
                double price = products.getDouble("price");
                productPrices.add(new ProductPrice(productId, price));
            }
        }

        try (PreparedStatement stmt = connection.prepareStatement("SELECT customerId FROM Customer;")) {
            ResultSet customers = stmt.executeQuery();
            while (customers.next()) {
                int customerId = customers.getInt(1);
                generateOrdersForCustomer(customerId, productPrices, connection, random);
            }
        }
    }

    private static void generateOrdersForCustomer(int customerId, List<ProductPrice> productPrices, Connection connection,
            Random random) throws SQLException {

        logger.fine(() -> "Generating orders for customer " + customerId);

        // here is how we do transactions in jdbc. By setting
        // auto commit to false we have the ability to decide which
        // statements go into the same transaction
        connection.setAutoCommit(false);

        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO CustomerOrder (customerId, orderDate, deliveryDate) VALUES (?, ?, ?);",
                Statement.RETURN_GENERATED_KEYS)) {

            LocalDate timeInThePast = LocalDate.now().minusDays(random.nextLong(179) + 1);
            LocalDate timeInTheFuture = LocalDate.now().plusDays(random.nextLong(179) + 1);

            stmt.setInt(1, customerId);
            stmt.setObject(2, timeInThePast);
            stmt.setObject(3, timeInTheFuture);

            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                generateOrderLines(keys.getInt(1), productPrices, connection, random);
            }

            // we commit when we have successfully generated a customer order and all
            // the order lines. 
            connection.commit();
        } catch (SQLException | RuntimeException ex) {
            // if something went wrong, we rollback everything and no partial order is
            // stored in the database
            connection.rollback();
            throw ex;
        } finally {
            
            // it is important to always reset the auto commit as to not
            // make other queries not work as intented
            connection.setAutoCommit(true);
        }
    }

    private static void generateOrderLines(int orderId, List<ProductPrice> productPrices, Connection connection, Random random)
            throws SQLException {

        logger.fine(() -> "Generating order lines for order " + orderId);

        final int itemCount = random.nextInt(5) + 1;
        final Set<ProductPrice> itemSet = new HashSet<>();
        while (itemSet.size() < itemCount) {
            itemSet.add(productPrices.get(random.nextInt(productPrices.size())));
        }

        try (PreparedStatement stmt = connection
                .prepareStatement("INSERT INTO OrderLine (orderId, productId, quantity, price) VALUES (?, ?, ?, ?);")) {

            stmt.setInt(1, orderId);

            for (ProductPrice item : itemSet) {
                stmt.setInt(2, item.productId());
                stmt.setInt(3, random.nextInt(3) + 1);
                stmt.setDouble(4, item.price());
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    private static boolean isEmptyDatabase(Connection connection) throws SQLException {
        return containsNoCustomers(connection) && containsNoProducts(connection);
    }

    private static boolean containsNoCustomers(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM Customer;")) {
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                return result.getInt(1) == 0;
            }

            return false;
        }
    }

    private static boolean containsNoProducts(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM Product;")) {
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                return result.getInt(1) == 0;
            }

            return false;
        }
    }
}
