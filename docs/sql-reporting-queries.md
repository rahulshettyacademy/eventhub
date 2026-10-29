# SQL Reporting Queries

Four MySQL 8 queries answer the reporting questions below. They run against the sample sales data in `DBTables.xlsx`. Mumbai leads city sales at 420.99, Electronics leads category revenue at 1,349.99, and Emily Johnson plus Priya Sharma are the high spenders.

## Tables

| Table | Columns | Rows |
| --- | --- | --- |
| customers | customer_id, name, email, city, country | 5 |
| orders | order_id, customer_id, order_date, total_amount | 6 |
| products | product_id, name, category, price | 5 |
| order_items | order_id, product_id, quantity | 7 |

The queries assume lowercase table names and `order_date` stored as `DATE`. If the real name is `order items` with a space, wrap it in backticks.

## 1. City with the highest total sales

Mumbai has the highest total sales at 420.99.

**MySQL 8**

```sql
SELECT c.city,
       SUM(o.total_amount) AS total_sales
FROM customers c
JOIN orders o ON o.customer_id = c.customer_id
GROUP BY c.city
ORDER BY total_sales DESC
LIMIT 1;
```

If two cities could tie for first place, this version returns all of them:

**MySQL 8 and SQL Server**

```sql
WITH city_sales AS (
  SELECT c.city,
         SUM(o.total_amount) AS total_sales,
         RANK() OVER (ORDER BY SUM(o.total_amount) DESC) AS rnk
  FROM customers c
  JOIN orders o ON o.customer_id = c.customer_id
  GROUP BY c.city
)
SELECT city, total_sales
FROM city_sales
WHERE rnk = 1;
```

| City | Total sales |
| --- | --- |
| Mumbai | 420.99 |
| New York | 385.75 |
| Hyderabad | 335.75 |
| Bangalore | 275.00 |

## 2. Top selling product categories

Electronics leads by revenue (1,349.99), and Apparel leads by units sold (5). Revenue is quantity × price.

**MySQL 8 and SQL Server**

```sql
SELECT p.category,
       SUM(oi.quantity)           AS units_sold,
       SUM(oi.quantity * p.price) AS revenue
FROM order_items oi
JOIN products p ON p.product_id = oi.product_id
GROUP BY p.category
ORDER BY revenue DESC;   -- or ORDER BY units_sold DESC
```

| Category | Units sold | Revenue |
| --- | --- | --- |
| Electronics | 2 | 1,349.99 |
| Apparel | 5 | 466.50 |
| Books | 4 | 63.96 |

## 3. Customers who haven't ordered recently

With a 30-day window, Emily Johnson and David Brown haven't ordered recently. The query also returns customers who have never placed an order.

For live data, measure from today:

**MySQL 8**

```sql
SELECT c.customer_id, c.name, c.email,
       MAX(o.order_date) AS last_order_date
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.customer_id
GROUP BY c.customer_id, c.name, c.email
HAVING last_order_date IS NULL
    OR last_order_date < CURDATE() - INTERVAL 90 DAY;
```

The sample orders are all from 2023–2024, so measured from today the query above returns every customer. For practice data, measure from the latest order date in the table instead:

**MySQL 8**

```sql
SELECT c.customer_id, c.name,
       MAX(o.order_date) AS last_order_date
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.customer_id
GROUP BY c.customer_id, c.name
HAVING last_order_date IS NULL
    OR last_order_date < (SELECT MAX(order_date) FROM orders) - INTERVAL 30 DAY;
```

| Customer | Last order | Reason |
| --- | --- | --- |
| Emily Johnson | 2024-06-22 | Before the cutoff of 2024-07-02 |
| David Brown | None | Has never ordered |

## 4. Average order value for high-spending customers

Priya Sharma (420.99) and Emily Johnson (385.75) are the high spenders. A high spender is any customer whose total spend is above the average total spend per customer, which is 354.37.

**MySQL 8 and SQL Server**

```sql
WITH customer_spend AS (
  SELECT customer_id,
         SUM(total_amount) AS total_spent,
         COUNT(*)          AS order_count
  FROM orders
  GROUP BY customer_id
)
SELECT c.name,
       cs.total_spent,
       cs.order_count,
       ROUND(cs.total_spent / cs.order_count, 2) AS avg_order_value
FROM customer_spend cs
JOIN customers c ON c.customer_id = cs.customer_id
WHERE cs.total_spent > (SELECT AVG(total_spent) FROM customer_spend)
ORDER BY avg_order_value DESC;
```

| Customer | Total spent | Orders | Avg order value |
| --- | --- | --- | --- |
| Priya Sharma | 420.99 | 1 | 420.99 |
| Emily Johnson | 385.75 | 1 | 385.75 |

To use a fixed threshold instead, replace the `WHERE` line with `WHERE cs.total_spent > 300`. Anika Reddy then also qualifies, with an average order value of 167.88.

## SQL Server versions

Queries 2 and 4 run unchanged on SQL Server (T-SQL). Queries 1 and 3 need the versions below.

| Query | Runs on SQL Server as written? | What changes |
| --- | --- | --- |
| 1. City with highest sales | No (the RANK version does) | `LIMIT 1` becomes `TOP 1` |
| 2. Top categories | Yes | Nothing |
| 3. Not ordered recently | No | `CURDATE()` and `INTERVAL` become `DATEADD` and `GETDATE()`. `HAVING` repeats `MAX(o.order_date)` instead of using the alias |
| 4. High spenders' AOV | Yes | Nothing |

**SQL Server (T-SQL)**: Query 1

```sql
SELECT TOP 1 c.city,
       SUM(o.total_amount) AS total_sales
FROM customers c
JOIN orders o ON o.customer_id = c.customer_id
GROUP BY c.city
ORDER BY total_sales DESC;
```

To include ties, use `SELECT TOP 1 WITH TIES`.

**SQL Server (T-SQL)**: Query 3, measured from today

```sql
SELECT c.customer_id, c.name, c.email,
       MAX(o.order_date) AS last_order_date
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.customer_id
GROUP BY c.customer_id, c.name, c.email
HAVING MAX(o.order_date) IS NULL
    OR MAX(o.order_date) < DATEADD(DAY, -90, CAST(GETDATE() AS DATE));
```

**SQL Server (T-SQL)**: Query 3, measured from the latest order date in the table

```sql
SELECT c.customer_id, c.name,
       MAX(o.order_date) AS last_order_date
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.customer_id
GROUP BY c.customer_id, c.name
HAVING MAX(o.order_date) IS NULL
    OR MAX(o.order_date) < DATEADD(DAY, -30, (SELECT MAX(order_date) FROM orders));
```

In SQL Server, a table name with a space is written as `[order items]`, not with backticks. Store `total_amount` as `DECIMAL(10,2)`. As an `INT`, the division in Query 4 becomes integer division and drops the decimals.

## Notes

The expected results were worked out by hand from the spreadsheet data. The queries have not yet been run against a live database.
