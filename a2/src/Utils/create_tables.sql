CREATE TABLE users(
    id INT PRIMARY KEY,
    username TEXT,
    email TEXT,
    password TEXT
);


CREATE TABLE products (
    id INT PRIMARY KEY,
    name TEXT,
    price DECIMAL
    quantity INT
);

CREATE TABLE orders(
    id SERIAL PRIMARY KEY,
    product_id INT,
    user_id INT,
    quantity INT,
    status TEXT
);