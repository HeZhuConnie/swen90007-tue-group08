package datasource;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import concurrency.LockManager;
import domain.Courier;
import domain.Customer;
import domain.Destination;
import domain.Item;
import domain.Order;
import domain.User;

public class OrderMapper implements IOrderMapper {

    private static final String findAllOrdersForCustomerLazy = "SELECT order_id FROM orders WHERE customer_id = ?";
    private static final String findOrderFromOrderId = "SELECT status, item_size, item_weight, destination_id, customer_id, courier_id FROM orders WHERE order_id = ?";
    private static final String updateOrderDetail = "UPDATE orders SET item_size = ?, item_weight = ?, destination_id = ? WHERE order_id = ?";
    private static final String insertNewOrder = "INSERT INTO orders(order_id, status, item_size, item_weight, destination_id, customer_id) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String deleteOrder = "DELETE FROM orders WHERE order_id = ?";

    private static final String findAllOrdersForCourierLazy = "SELECT order_id FROM orders WHERE courier_id = ? AND status = ?";
    private static final String findOrdersWithStatus = "SELECT order_id FROM orders WHERE status = ?";
    private static final String updateOrderShipment = "UPDATE orders SET status = ?, courier_id = ? WHERE order_id = ?";

    /**
     * Retrieve order object from Identity Map according to order_id. If the
     * corresponding order is not registered in the Identity Map, fetch the data
     * from database and register it into Identity Map.
     * 
     * @param order_id
     *            The id of the order to be retrieved.
     * @param lazy
     *            Whether use the lazy mode or not.
     * @return Retrieved Order.
     */
    private Order checkIdentityMap(int order_id, boolean lazy) {
        Order order = new Order();
        IdentityMap<Order> iMap = IdentityMap.getInstance(order);
        order = iMap.get(order_id);
        if (order == null) {
            if (lazy) {
                order = new Order(order_id);
            } else {
                PreparedStatement findStatement = null;
                ResultSet rs = null;
                try {
                    findStatement = DBConnection.prepare(findOrderFromOrderId);
                    findStatement.setInt(1, order_id);
                    rs = findStatement.executeQuery();
                    rs.next();
                    String status = rs.getString(1);

                    float item_size = rs.getFloat(2);
                    float item_weight = rs.getFloat(3);
                    Item item = new Item(item_size, item_weight);

                    int destination_id = rs.getInt(4);
                    int customer_id = rs.getInt(5);
                    int courier_id = rs.getInt(6);

                    UserMapper uMapper = new UserMapper();
                    User customer = uMapper.findWithUserId(customer_id,
                            User.CUSTOMER_TYPE, false);
                    User courier = uMapper.findWithUserId(courier_id,
                            User.COURIER_TYPE, true);

                    DestinationMapper dMapper = new DestinationMapper();
                    Destination destination = dMapper
                            .findDestinationFromDestinationId(destination_id);
                    order = new Order(order_id, status, item, destination,
                            (Customer) customer, (Courier) courier);
                    iMap.put(order_id, order);
                    LockManager.getInstance().acquireReadLock(order);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (SQLException e) {
                    try {
                        DBConnection.dbConnection.rollback();
                    } catch (SQLException ignored) {
                        System.err.println("Rollback failed.");
                    }
                } finally {
                    try {
                        if (DBConnection.dbConnection != null)
                            DBConnection.dbConnection.close();
                    } catch (SQLException ignored) {
                    }
                }
            }
        }
        return order;
    }

    /**
     * Fetch all orders for one customer according to his/her id.
     * 
     * @param customer_id
     *            The id of the customer for whom retrieve the orders.
     * @return Retrieved list of orders.
     */
    public List<Order> findAllOrdersForCustomer(int customer_id) {
        PreparedStatement findStatement = null;
        ResultSet rs = null;
        List<Order> orders = new ArrayList<>();
        try {
            findStatement = DBConnection.prepare(findAllOrdersForCustomerLazy);
            findStatement.setInt(1, customer_id);
            rs = findStatement.executeQuery();
            while (rs.next()) {
                int order_id = rs.getInt(1);
                Order order = checkIdentityMap(order_id, true);
                orders.add(order);
            }
        } catch (SQLException e) {
            try {
                DBConnection.dbConnection.rollback();
            } catch (SQLException ignored) {
                System.err.println("Rollback failed.");
            }
        } finally {
            try {
                if (DBConnection.dbConnection != null)
                    DBConnection.dbConnection.close();
            } catch (SQLException ignored) {
            }
        }
        return orders;
    }

    /**
     * Retrieve all data of one order. Lazy mode off.
     * 
     * @param order_id
     *            The id of the order.
     * @return Retrieved order.
     * @throws SQLException
     */
    public Order findOrderFromOrderId(int order_id) throws SQLException {
        return checkIdentityMap(order_id, false);
    }

    public void insert(Order order) {
        PreparedStatement insertStatement = null;
        try {
            insertStatement = DBConnection.prepare(insertNewOrder);
            insertStatement.setInt(1, order.getOrder_id());
            insertStatement.setString(2, order.getStatus());
            insertStatement.setFloat(3, order.getItem().getItem_size());
            insertStatement.setFloat(4, order.getItem().getItem_weight());
            insertStatement.setInt(5,
                    order.getDestination().getDestination_id());
            insertStatement.setInt(6, order.getCustomer().getUser_id());
            insertStatement.execute();
        } catch (SQLException e) {
            try {
                DBConnection.dbConnection.rollback();
            } catch (SQLException ignored) {
                System.err.println("Rollback failed.");
            }
        } finally {
            try {
                if (DBConnection.dbConnection != null)
                    DBConnection.dbConnection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Update the details(item information, destination) of the order.
     * 
     * @param order
     */
    public void updateDetailOfOrder(Order order) {
        PreparedStatement updateStatement = null;
        try {
            updateStatement = DBConnection.prepare(updateOrderDetail);
            updateStatement.setFloat(1, order.getItem().getItem_size());
            updateStatement.setFloat(2, order.getItem().getItem_weight());
            updateStatement.setInt(3,
                    order.getDestination().getDestination_id());
            updateStatement.setInt(4, order.getOrder_id());
            updateStatement.execute();
        } catch (SQLException e) {
            try {
                DBConnection.dbConnection.rollback();
            } catch (SQLException ignored) {
                System.err.println("Rollback failed.");
            }
        } finally {
            try {
                if (DBConnection.dbConnection != null)
                    DBConnection.dbConnection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    public void delete(Order order) {
        PreparedStatement deleteStatement = null;
        try {
            deleteStatement = DBConnection.prepare(deleteOrder);
            deleteStatement.setInt(1, order.getOrder_id());
            deleteStatement.execute();
        } catch (SQLException e) {
            try {
                DBConnection.dbConnection.rollback();
            } catch (SQLException ignored) {
                System.err.println("Rollback failed.");
            }
        } finally {
            try {
                if (DBConnection.dbConnection != null)
                    DBConnection.dbConnection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Fetch all orders for one courier according to his/her id.
     * 
     * @param courier_id
     *            The id of the courier for whom retrieve the orders.
     * @param status
     *            The status of the orders need to be retrieved
     * @return Retrieved list of orders.
     */
    public List<Order> findAllOrdersForCourier(int courier_id, String status) {
        PreparedStatement findStatement = null;
        ResultSet rs = null;
        List<Order> orders = new ArrayList<>();
        try {
            findStatement = DBConnection.prepare(findAllOrdersForCourierLazy);
            findStatement.setInt(1, courier_id);
            findStatement.setString(2, status);
            rs = findStatement.executeQuery();
            while (rs.next()) {
                int order_id = rs.getInt(1);
                Order order = checkIdentityMap(order_id, true);
                orders.add(order);
            }
        } catch (SQLException e) {
            try {
                DBConnection.dbConnection.rollback();
            } catch (SQLException ignored) {
                System.err.println("Rollback failed.");
            }
        } finally {
            try {
                if (DBConnection.dbConnection != null)
                    DBConnection.dbConnection.close();
            } catch (SQLException ignored) {
            }
        }
        return orders;
    }

    /**
     * Retrieve all confirmed(new) orders
     * 
     * @return Retrieved list of orders.
     */
    public List<Order> findAllConfirmedOrders() {
        PreparedStatement findStatement = null;
        ResultSet rs = null;
        List<Order> orders = new ArrayList<>();
        try {
            findStatement = DBConnection.prepare(findOrdersWithStatus);
            findStatement.setString(1, "Confirmed");
            rs = findStatement.executeQuery();
            while (rs.next()) {
                int order_id = rs.getInt(1);
                Order order = checkIdentityMap(order_id, true);
                orders.add(order);
            }
        } catch (SQLException e) {
            try {
                DBConnection.dbConnection.rollback();
            } catch (SQLException ignored) {
                System.err.println("Rollback failed.");
            }
        } finally {
            try {
                if (DBConnection.dbConnection != null)
                    DBConnection.dbConnection.close();
            } catch (SQLException ignored) {
            }
        }
        return orders;
    }

    /**
     * Update the shipment information of the order.
     * 
     * @param order
     */
    public void updateShipmentOfOrder(Order order) {
        PreparedStatement updateStatement = null;
        try {
            updateStatement = DBConnection.prepare(updateOrderShipment);
            updateStatement.setString(1, order.getStatus());
            updateStatement.setInt(2, order.getCourier().getUser_id());
            updateStatement.setInt(3, order.getOrder_id());
            updateStatement.execute();
        } catch (SQLException e) {
            try {
                DBConnection.dbConnection.rollback();
            } catch (SQLException ignored) {
                System.err.println("Rollback failed.");
            }
        } finally {
            try {
                if (DBConnection.dbConnection != null)
                    DBConnection.dbConnection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
