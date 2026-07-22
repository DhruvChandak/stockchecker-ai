package com.stockpilot.ai.service;

import com.stockpilot.ai.domain.DomainEnums;
import com.stockpilot.ai.exception.ApiErrors;
import com.stockpilot.ai.repo.Repositories;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OutstandingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final Repositories.CustomerRepository customers;
    private final Repositories.SupplierRepository suppliers;
    private final Repositories.SalesInvoiceRepository salesInvoices;
    private final Repositories.PurchaseInvoiceRepository purchaseInvoices;
    private final Repositories.CustomerPaymentRepository customerPayments;
    private final Repositories.SupplierPaymentRepository supplierPayments;
    private final Repositories.OutstandingSnapshotRepository snapshots;
    private final Repositories.FinancialAdjustmentRepository adjustments;

    public OutstandingService(
        Repositories.CustomerRepository customers,
        Repositories.SupplierRepository suppliers,
        Repositories.SalesInvoiceRepository salesInvoices,
        Repositories.PurchaseInvoiceRepository purchaseInvoices,
        Repositories.CustomerPaymentRepository customerPayments,
        Repositories.SupplierPaymentRepository supplierPayments,
        Repositories.OutstandingSnapshotRepository snapshots,
        Repositories.FinancialAdjustmentRepository adjustments
    ) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.salesInvoices = salesInvoices;
        this.purchaseInvoices = purchaseInvoices;
        this.customerPayments = customerPayments;
        this.supplierPayments = supplierPayments;
        this.snapshots = snapshots;
        this.adjustments = adjustments;
    }

    public BigDecimal customerOutstanding(UUID tenantId, UUID customerId) {
        var customer = customers.findByTenantIdAndId(tenantId, customerId)
            .orElseThrow(() -> ApiErrors.notFound("Customer not found"));
        var snapshot = snapshots.findFirstByTenantIdAndCustomerIdOrderBySnapshotDateDescCreatedAtDesc(tenantId, customerId)
            .orElse(null);
        var base = snapshot == null ? nvl(customer.openingBalance) : nvl(snapshot.outstandingAmount);
        var invoices = snapshot == null ? salesInvoices.findByTenantIdAndCustomerId(tenantId, customerId)
            : salesInvoices.findByTenantIdAndCustomerIdAndInvoiceDateAfter(tenantId, customerId, snapshot.snapshotDate);
        var payments = snapshot == null ? customerPayments.findByTenantIdAndCustomerIdOrderByPaymentDateDesc(tenantId, customerId)
            : customerPayments.findByTenantIdAndCustomerIdAndPaymentDateAfter(tenantId, customerId, snapshot.snapshotDate);
        var credits = adjustments.findByTenantIdOrderByVoucherDateDesc(tenantId).stream()
            .filter(row -> row.adjustmentType == DomainEnums.FinancialAdjustmentType.CREDIT_NOTE)
            .filter(row -> customerId.equals(row.customerId))
            .filter(row -> snapshot == null || row.voucherDate.isAfter(snapshot.snapshotDate))
            .map(row -> nvl(row.totalAmount)).reduce(ZERO, BigDecimal::add);
        return base.add(invoices.stream().map(row -> nvl(row.totalAmount)).reduce(ZERO, BigDecimal::add))
            .subtract(payments.stream().map(row -> nvl(row.amount)).reduce(ZERO, BigDecimal::add))
            .subtract(credits);
    }

    public BigDecimal supplierPayable(UUID tenantId, UUID supplierId) {
        var supplier = suppliers.findByTenantIdAndId(tenantId, supplierId)
            .orElseThrow(() -> ApiErrors.notFound("Supplier not found"));
        var snapshot = snapshots.findFirstByTenantIdAndSupplierIdOrderBySnapshotDateDescCreatedAtDesc(tenantId, supplierId)
            .orElse(null);
        var base = snapshot == null ? nvl(supplier.openingBalance) : nvl(snapshot.outstandingAmount);
        var invoices = snapshot == null ? purchaseInvoices.findByTenantIdAndSupplierId(tenantId, supplierId)
            : purchaseInvoices.findByTenantIdAndSupplierIdAndInvoiceDateAfter(tenantId, supplierId, snapshot.snapshotDate);
        var payments = snapshot == null ? supplierPayments.findByTenantIdAndSupplierIdOrderByPaymentDateDesc(tenantId, supplierId)
            : supplierPayments.findByTenantIdAndSupplierIdAndPaymentDateAfter(tenantId, supplierId, snapshot.snapshotDate);
        var debits = adjustments.findByTenantIdOrderByVoucherDateDesc(tenantId).stream()
            .filter(row -> row.adjustmentType == DomainEnums.FinancialAdjustmentType.DEBIT_NOTE)
            .filter(row -> supplierId.equals(row.supplierId))
            .filter(row -> snapshot == null || row.voucherDate.isAfter(snapshot.snapshotDate))
            .map(row -> nvl(row.totalAmount)).reduce(ZERO, BigDecimal::add);
        return base.add(invoices.stream().map(row -> nvl(row.totalAmount)).reduce(ZERO, BigDecimal::add))
            .subtract(payments.stream().map(row -> nvl(row.amount)).reduce(ZERO, BigDecimal::add))
            .subtract(debits);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
