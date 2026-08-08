USE SkySport
GO

SET NOCOUNT ON
GO

-- Indexes used by dashboard/report date and status filters.
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_Bill_CreateDate_Status'
      AND object_id = OBJECT_ID(N'dbo.Bill')
)
BEGIN
    CREATE INDEX IX_Bill_CreateDate_Status
        ON dbo.Bill(create_date, status)
        INCLUDE (amount, invoice_type, customer_id)
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'IX_BillDetail_Bill'
      AND object_id = OBJECT_ID(N'dbo.Bill_detail')
)
BEGIN
    CREATE INDEX IX_BillDetail_Bill
        ON dbo.Bill_detail(bill_id)
        INCLUDE (product_detail_id, quantity, moment_price)
END
GO

/*
 * Dữ liệu demo cho hai màn Dashboard và Thống kê.
 * Chỉ xóa/tạo lại các hóa đơn có tiền tố TKDEMO, không ảnh hưởng dữ liệu thật.
 * Ngày được tính từ GETDATE() nên biểu đồ 7/30 ngày luôn có dữ liệu khi khởi tạo.
 */
DELETE FROM dbo.Bill WHERE code LIKE N'TKDEMO%'
GO

DECLARE @i INT = 0
DECLARE @billId INT
DECLARE @productCount INT = (SELECT COUNT(*) FROM dbo.Product_detail WHERE delete_flag = 0)
DECLARE @pd1 INT
DECLARE @pd2 INT
DECLARE @price1 DECIMAL(18,2)
DECLARE @price2 DECIMAL(18,2)
DECLARE @qty1 INT
DECLARE @qty2 INT
DECLARE @status INT
DECLARE @invoiceType INT
DECLARE @createdAt DATETIME
DECLARE @subtotal DECIMAL(18,2)
DECLARE @shippingFee DECIMAL(18,2)
DECLARE @promotion DECIMAL(18,2)

IF @productCount = 0
BEGIN
    THROW 50001, N'Không thể tạo dữ liệu thống kê: Product_detail đang trống.', 1
END

WHILE @i < 45
BEGIN
    -- Mỗi chu kỳ 11 ngày có đủ các trạng thái, phần lớn là đơn hoàn thành.
    SET @status = CASE (@i % 11)
        WHEN 6 THEN 6 -- Trả hàng
        WHEN 7 THEN 4 -- Đã giao
        WHEN 8 THEN 3 -- Đang giao
        WHEN 9 THEN 1 -- Chờ xác nhận
        WHEN 10 THEN 5 -- Đã hủy
        ELSE 7 -- Hoàn thành
    END

    SET @invoiceType = CASE WHEN @i % 3 = 0 THEN 2 ELSE 1 END
    SET @createdAt = DATEADD(
        HOUR,
        8 + (@i % 10),
        CAST(DATEADD(DAY, -@i, CAST(GETDATE() AS DATE)) AS DATETIME)
    )
    SET @qty1 = 1 + (@i % 3)
    SET @qty2 = 1 + ((@i + 1) % 2)

    SELECT @pd1 = id, @price1 = price
    FROM dbo.Product_detail
    WHERE delete_flag = 0
    ORDER BY id
    OFFSET (@i % @productCount) ROWS FETCH NEXT 1 ROW ONLY

    SELECT @pd2 = id, @price2 = price
    FROM dbo.Product_detail
    WHERE delete_flag = 0
    ORDER BY id
    OFFSET ((@i * 3 + 5) % @productCount) ROWS FETCH NEXT 1 ROW ONLY

    SET @subtotal = (@price1 * @qty1) + (@price2 * @qty2)
    SET @shippingFee = CASE WHEN @invoiceType = 1 AND @subtotal < 1500000 THEN 30000 ELSE 0 END
    SET @promotion = CASE WHEN @i % 5 = 0 THEN 50000 ELSE 0 END

    INSERT INTO dbo.Bill (
        code, note, invoice_type, billing_address,
        subtotal, shipping_fee, promotion_price, amount,
        status, return_status, discount_code_id,
        customer_id, payment_id, create_date, update_date
    )
    VALUES (
        N'TKDEMO' + RIGHT(N'000' + CAST(@i + 1 AS NVARCHAR(3)), 3),
        N'Dữ liệu mẫu phục vụ biểu đồ thống kê',
        @invoiceType,
        CASE (@i % 3)
            WHEN 0 THEN N'12 Lê Lợi, TP.HCM'
            WHEN 1 THEN N'78 Nguyễn Huệ, Hà Nội'
            ELSE N'45 Trần Phú, Đà Nẵng'
        END,
        @subtotal,
        @shippingFee,
        @promotion,
        @subtotal + @shippingFee - @promotion,
        @status,
        CASE WHEN @status = 6 THEN 1 ELSE 0 END,
        NULL,
        1 + (@i % 3),
        CASE WHEN @invoiceType = 2 THEN 1 ELSE 1 + (@i % 4) END,
        @createdAt,
        DATEADD(HOUR, CASE WHEN @status = 7 THEN 6 ELSE 1 END, @createdAt)
    )

    SET @billId = SCOPE_IDENTITY()

    INSERT INTO dbo.Bill_detail (
        moment_price, quantity, return_quantity, bill_id, product_detail_id
    )
    VALUES
        (@price1, @qty1, CASE WHEN @status = 6 THEN 1 ELSE 0 END, @billId, @pd1),
        (@price2, @qty2, 0, @billId, @pd2)

    SET @i = @i + 1
END
GO

SELECT
    COUNT(*) AS DemoOrders,
    SUM(CASE WHEN status = 7 THEN 1 ELSE 0 END) AS CompletedOrders,
    SUM(CASE WHEN status = 7 THEN amount ELSE 0 END) AS CompletedRevenue,
    MIN(create_date) AS FromDate,
    MAX(create_date) AS ToDate
FROM dbo.Bill
WHERE code LIKE N'TKDEMO%'
GO
