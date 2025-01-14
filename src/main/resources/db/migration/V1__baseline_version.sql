CREATE TABLE Customer
(
    customerId  INT             NOT NULL IDENTITY(1,1),
    name        VARCHAR(30)     NOT NULL,
    address     VARCHAR(45)     NOT NULL,
    zipCode     VARCHAR(6)      NOT NULL,
    city        VARCHAR(45)     NOT NULL,
    discount    DECIMAL(3, 2)   NOT NULL DEFAULT 0.00,
    PRIMARY KEY (customerId),
    CHECK (discount <= 1 AND discount >= 0)
);
GO

CREATE TABLE Product
(
    productId    INT            NOT NULL IDENTITY(1,1),
    productName  VARCHAR(45)    NOT NULL,
    stock        INT            NOT NULL,
    reorderPoint INT DEFAULT    NULL,
    price        DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (productId)
);
GO

CREATE TABLE CustomerOrder
(
    orderId      INT        NOT NULL IDENTITY(1,1),
    customerId   INT        NOT NULL,
    orderDate    DATETIME2  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deliveryDate DATETIME2  NOT NULL,
    PRIMARY KEY (orderId),
    FOREIGN KEY (customerId) REFERENCES Customer(customerId),
    CHECK (deliveryDate >= orderDate)
);
GO

CREATE TABLE OrderLine
(
    orderId     INT NOT NULL,
    productId   INT NOT NULL,
    quantity    INT NOT NULL,
    price       DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (orderId, productId),
    FOREIGN KEY (orderId) REFERENCES CustomerOrder(orderId),
    FOREIGN KEY (productId) REFERENCES Product(productId)
);
GO
