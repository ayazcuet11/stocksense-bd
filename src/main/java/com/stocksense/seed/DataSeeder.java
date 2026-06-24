package com.stocksense.seed;

import com.stocksense.domain.*;
import com.stocksense.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Component
@RequiredArgsConstructor
@Profile("!prod")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TenantRepository tenantRepo;
    private final BranchRepository branchRepo;
    private final UserRepository userRepo;
    private final CategoryRepository categoryRepo;
    private final ProductRepository productRepo;
    private final BranchStockRepository stockRepo;
    private final SupplierRepository supplierRepo;
    private final ProductSupplierRepository productSupplierRepo;
    private final SaleRepository saleRepo;
    private final PasswordEncoder passwordEncoder;

    // Festival windows (month, day) -> multiplier for daily sales
    private static final Map<String, Double> FESTIVAL_MULTIPLIERS = new LinkedHashMap<>();

    static {
        // Eid ul-Fitr 2024: April 10 ± 10 days
        FESTIVAL_MULTIPLIERS.put("2024-04", 2.8);
        // Pohela Boishakh: April 14
        // Eid ul-Adha 2024: June 17 ± 10 days
        FESTIVAL_MULTIPLIERS.put("2024-06", 2.5);
        // Mango season May-July
        FESTIVAL_MULTIPLIERS.put("2024-05", 1.8);
        FESTIVAL_MULTIPLIERS.put("2024-07", 1.6);
        // Monsoon
        FESTIVAL_MULTIPLIERS.put("2024-08", 1.4);
        // Hilsa season Aug-Sep
        FESTIVAL_MULTIPLIERS.put("2024-09", 1.5);
        // Durga Puja: October 12
        FESTIVAL_MULTIPLIERS.put("2024-10", 1.9);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (tenantRepo.count() > 0) {
            log.info("Seed data already present, skipping.");
            return;
        }
        log.info("Seeding StockSense BD demo data...");

        Tenant tenant = new Tenant();
        tenant.setName("Rahim Brothers Retail");
        tenant = tenantRepo.save(tenant);
        Long tid = tenant.getId();

        List<Branch> branches = seedBranches(tid);
        seedUsers(tid, branches);
        List<Category> categories = seedCategories(tid);
        List<Supplier> suppliers = seedSuppliers(tid);
        List<Product> products = seedProducts(tid, categories);
        seedProductPrices(products, suppliers);
        seedStock(branches, products);
        seedSales(branches, products);

        log.info("Seed complete: 1 tenant, {} branches, {} products, 12 months of sales.", branches.size(), products.size());
    }

    private List<Branch> seedBranches(Long tid) {
        return branchRepo.saveAll(List.of(
                branch(tid, "Mirpur Branch", "Road 10, Section 10, Mirpur", "Dhaka", "01700000001"),
                branch(tid, "Gulshan Branch", "Road 8, Block E, Gulshan-1", "Dhaka", "01700000002"),
                branch(tid, "Chittagong Branch", "Agrabad C/A, Chittagong", "Chittagong", "01700000003")
        ));
    }

    private void seedUsers(Long tid, List<Branch> branches) {
        AppUser owner = new AppUser();
        owner.setTenantId(tid); owner.setName("Admin"); owner.setEmail("admin@example.com");
        owner.setPasswordHash(passwordEncoder.encode("password")); owner.setRole(Role.OWNER);
        userRepo.save(owner);

        for (int i = 0; i < branches.size(); i++) {
            AppUser mgr = new AppUser();
            mgr.setTenantId(tid); mgr.setName("Manager " + (i + 1));
            mgr.setEmail("manager" + (i + 1) + "@rahimbrothers.com");
            mgr.setPasswordHash(passwordEncoder.encode("password"));
            mgr.setRole(Role.MANAGER); mgr.setBranchId(branches.get(i).getId());
            userRepo.save(mgr);
        }
    }

    private List<Category> seedCategories(Long tid) {
        return categoryRepo.saveAll(List.of(
                category(tid, "Beverages"),
                category(tid, "Food & Grocery"),
                category(tid, "Personal Care"),
                category(tid, "Seasonal"),
                category(tid, "Festival & Puja"),
                category(tid, "Dairy"),
                category(tid, "Snacks"),
                category(tid, "Fresh Produce")
        ));
    }

    private List<Supplier> seedSuppliers(Long tid) {
        return supplierRepo.saveAll(List.of(
                supplier(tid, "Akij Food & Beverage Ltd", "01900000001", 2),
                supplier(tid, "Pran-RFL Group", "01900000002", 3),
                supplier(tid, "Square Toiletries", "01900000003", 4),
                supplier(tid, "ACI Limited", "01900000004", 3),
                supplier(tid, "Partex Beverage", "01900000005", 2)
        ));
    }

    private List<Product> seedProducts(Long tid, List<Category> cats) {
        long bevId = cats.get(0).getId(), foodId = cats.get(1).getId(),
             careId = cats.get(2).getId(), seasonalId = cats.get(3).getId(),
             festId = cats.get(4).getId(), dairyId = cats.get(5).getId(),
             snackId = cats.get(6).getId(), produceId = cats.get(7).getId();

        List<Object[]> defs = List.of(
            // Beverages
            new Object[]{"BEV-001","Rooh Afza 750ml",bevId,"bottle",new BigDecimal("0.00")},
            new Object[]{"BEV-002","Shezan Mango Juice 250ml",bevId,"pack",new BigDecimal("0.00")},
            new Object[]{"BEV-003","RC Cola 1L",bevId,"bottle",new BigDecimal("0.00")},
            new Object[]{"BEV-004","Fizz Up 250ml",bevId,"can",new BigDecimal("0.00")},
            new Object[]{"BEV-005","Mineral Water 500ml",bevId,"bottle",new BigDecimal("0.00")},
            new Object[]{"BEV-006","Horlicks 500g",bevId,"jar",new BigDecimal("0.00")},
            new Object[]{"BEV-007","Tang Orange 500g",bevId,"pouch",new BigDecimal("0.00")},
            new Object[]{"BEV-008","Ovaltine 400g",bevId,"tin",new BigDecimal("0.00")},
            new Object[]{"BEV-009","Coffee Mate 200g",bevId,"jar",new BigDecimal("0.00")},
            new Object[]{"BEV-010","Nestea Lemon 500ml",bevId,"bottle",new BigDecimal("0.00")},
            // Food & Grocery
            new Object[]{"FOOD-001","Miniket Rice 5kg",foodId,"bag",new BigDecimal("0.00")},
            new Object[]{"FOOD-002","Soybean Oil 2L",foodId,"bottle",new BigDecimal("15.00")},
            new Object[]{"FOOD-003","Wheat Flour 2kg",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-004","Semolina 500g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-005","Lentil Red 500g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-006","Sugar 1kg",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-007","Salt 500g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-008","Turmeric Powder 200g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-009","Chili Powder 200g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-010","Coriander Powder 200g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-011","Basmati Rice 5kg",foodId,"bag",new BigDecimal("0.00")},
            new Object[]{"FOOD-012","Vermicelli 200g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-013","Chickpeas 500g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-014","Date Paste 500g",foodId,"jar",new BigDecimal("0.00")},
            new Object[]{"FOOD-015","Ghee 500g",foodId,"tin",new BigDecimal("0.00")},
            new Object[]{"FOOD-016","Condensed Milk 397g",foodId,"tin",new BigDecimal("0.00")},
            new Object[]{"FOOD-017","Tomato Sauce 340g",foodId,"bottle",new BigDecimal("15.00")},
            new Object[]{"FOOD-018","Soy Sauce 200ml",foodId,"bottle",new BigDecimal("0.00")},
            new Object[]{"FOOD-019","Vinegar 500ml",foodId,"bottle",new BigDecimal("0.00")},
            new Object[]{"FOOD-020","Mustard Oil 1L",foodId,"bottle",new BigDecimal("0.00")},
            // Personal Care
            new Object[]{"CARE-001","Fair & Lovely 50g",careId,"tube",new BigDecimal("0.00")},
            new Object[]{"CARE-002","Lifebuoy Soap 100g",careId,"bar",new BigDecimal("0.00")},
            new Object[]{"CARE-003","Lux Soap 100g",careId,"bar",new BigDecimal("0.00")},
            new Object[]{"CARE-004","Sunsilk Shampoo 180ml",careId,"bottle",new BigDecimal("0.00")},
            new Object[]{"CARE-005","Pantene Shampoo 180ml",careId,"bottle",new BigDecimal("0.00")},
            new Object[]{"CARE-006","Colgate Toothpaste 100g",careId,"tube",new BigDecimal("0.00")},
            new Object[]{"CARE-007","Meril Body Lotion 200ml",careId,"bottle",new BigDecimal("0.00")},
            new Object[]{"CARE-008","Dettol Antiseptic 100ml",careId,"bottle",new BigDecimal("0.00")},
            new Object[]{"CARE-009","Nivea Cream 50ml",careId,"tin",new BigDecimal("0.00")},
            new Object[]{"CARE-010","Vaseline 100g",careId,"jar",new BigDecimal("0.00")},
            new Object[]{"CARE-011","Rexona Deo 150ml",careId,"spray",new BigDecimal("0.00")},
            new Object[]{"CARE-012","Dove Soap 75g",careId,"bar",new BigDecimal("0.00")},
            // Seasonal
            new Object[]{"SEA-001","Umbrella Compact",seasonalId,"piece",new BigDecimal("0.00")},
            new Object[]{"SEA-002","Raincoat Adult",seasonalId,"piece",new BigDecimal("0.00")},
            new Object[]{"SEA-003","Mosquito Coil 10pk",seasonalId,"pack",new BigDecimal("0.00")},
            new Object[]{"SEA-004","Electric Fan Handheld",seasonalId,"piece",new BigDecimal("0.00")},
            new Object[]{"SEA-005","ORS Sachet 5pk",seasonalId,"pack",new BigDecimal("0.00")},
            // Festival & Puja
            new Object[]{"FEST-001","Agarbatti Incense 50pk",festId,"pack",new BigDecimal("0.00")},
            new Object[]{"FEST-002","Dhoop Stick 20pk",festId,"pack",new BigDecimal("0.00")},
            new Object[]{"FEST-003","Candle Diya 12pk",festId,"pack",new BigDecimal("0.00")},
            new Object[]{"FEST-004","Sindoor Box",festId,"piece",new BigDecimal("0.00")},
            new Object[]{"FEST-005","Eid Gift Box Sweets",festId,"box",new BigDecimal("0.00")},
            new Object[]{"FEST-006","Sewain (Vermicelli) 500g",festId,"packet",new BigDecimal("0.00")},
            new Object[]{"FEST-007","Dates (Khajur) 500g",festId,"packet",new BigDecimal("0.00")},
            new Object[]{"FEST-008","Attar Perfume 8ml",festId,"bottle",new BigDecimal("0.00")},
            // Dairy
            new Object[]{"DAIRY-001","Fresh Milk 1L",dairyId,"carton",new BigDecimal("0.00")},
            new Object[]{"DAIRY-002","Dahi (Yogurt) 400g",dairyId,"cup",new BigDecimal("0.00")},
            new Object[]{"DAIRY-003","Butter 200g",dairyId,"pack",new BigDecimal("0.00")},
            new Object[]{"DAIRY-004","Cheese Slice 200g",dairyId,"pack",new BigDecimal("0.00")},
            new Object[]{"DAIRY-005","Mishti Doi 250g",dairyId,"cup",new BigDecimal("0.00")},
            // Snacks
            new Object[]{"SNACK-001","Bombay Sweets Mix 250g",snackId,"pack",new BigDecimal("0.00")},
            new Object[]{"SNACK-002","Chips Crackers 100g",snackId,"pack",new BigDecimal("0.00")},
            new Object[]{"SNACK-003","Chanachur 200g",snackId,"pack",new BigDecimal("0.00")},
            new Object[]{"SNACK-004","Biscuit Glucose 200g",snackId,"pack",new BigDecimal("0.00")},
            new Object[]{"SNACK-005","Marie Biscuit 200g",snackId,"pack",new BigDecimal("0.00")},
            new Object[]{"SNACK-006","Chocolate Bar 50g",snackId,"piece",new BigDecimal("0.00")},
            new Object[]{"SNACK-007","Noodles Instant 75g",snackId,"pack",new BigDecimal("0.00")},
            new Object[]{"SNACK-008","Popcorn Salted 100g",snackId,"pack",new BigDecimal("0.00")},
            // Fresh Produce
            new Object[]{"PROD-001","Alphonso Mango 1kg",produceId,"kg",new BigDecimal("0.00")},
            new Object[]{"PROD-002","Hilsa Fish 1kg",produceId,"kg",new BigDecimal("0.00")},
            new Object[]{"PROD-003","Green Banana 1kg",produceId,"kg",new BigDecimal("0.00")},
            new Object[]{"PROD-004","Potato 1kg",produceId,"kg",new BigDecimal("0.00")},
            new Object[]{"PROD-005","Onion 1kg",produceId,"kg",new BigDecimal("0.00")},
            new Object[]{"PROD-006","Tomato 1kg",produceId,"kg",new BigDecimal("0.00")},
            new Object[]{"PROD-007","Garlic 250g",produceId,"pack",new BigDecimal("0.00")},
            new Object[]{"PROD-008","Ginger 250g",produceId,"pack",new BigDecimal("0.00")},
            new Object[]{"PROD-009","Green Chili 250g",produceId,"pack",new BigDecimal("0.00")},
            new Object[]{"PROD-010","Coriander Leaf 100g",produceId,"bunch",new BigDecimal("0.00")},
            // Extra food to hit ~100
            new Object[]{"FOOD-021","Mustard Seed 200g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-022","Cumin Seed 100g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-023","Black Pepper 50g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-024","Cardamom 50g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-025","Cinnamon Stick 50g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-026","Bay Leaf 20g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-027","Fenugreek Seed 100g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-028","Nutmeg Powder 50g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-029","Clove 20g",foodId,"packet",new BigDecimal("0.00")},
            new Object[]{"FOOD-030","Anise Seed 50g",foodId,"packet",new BigDecimal("0.00")}
        );

        List<Product> products = new ArrayList<>();
        for (Object[] d : defs) {
            Product p = new Product();
            p.setTenantId(tid);
            p.setSku((String) d[0]);
            p.setName((String) d[1]);
            p.setCategoryId((Long) d[2]);
            p.setUnit((String) d[3]);
            p.setVatRate((BigDecimal) d[4]);
            products.add(p);
        }
        return productRepo.saveAll(products);
    }

    private void seedProductPrices(List<Product> products, List<Supplier> suppliers) {
        List<ProductSupplier> prices = new ArrayList<>();
        Random rng = new Random(42L);
        for (Product p : products) {
            int numSuppliers = 1 + rng.nextInt(Math.min(3, suppliers.size()));
            List<Supplier> shuffled = new ArrayList<>(suppliers);
            Collections.shuffle(shuffled, rng);
            for (int i = 0; i < numSuppliers; i++) {
                double basePrice = 30 + rng.nextDouble() * 470;
                ProductSupplier ps = new ProductSupplier();
                ps.setId(new ProductSupplierId(p.getId(), shuffled.get(i).getId()));
                ps.setUnitPrice(BigDecimal.valueOf(Math.round(basePrice * 100.0) / 100.0));
                prices.add(ps);
            }
        }
        productSupplierRepo.saveAll(prices);
    }

    private void seedStock(List<Branch> branches, List<Product> products) {
        List<BranchStock> stocks = new ArrayList<>();
        Random rng = new Random(7L);
        for (Branch b : branches) {
            for (Product p : products) {
                BranchStock s = new BranchStock();
                s.setBranchId(b.getId());
                s.setProductId(p.getId());
                s.setQuantity(50 + rng.nextInt(450));
                s.setReorderThreshold(20 + rng.nextInt(30));
                stocks.add(s);
            }
        }
        stockRepo.saveAll(stocks);
    }

    private void seedSales(List<Branch> branches, List<Product> products) {
        Random rng = new Random(99L);
        List<Sale> sales = new ArrayList<>();

        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 12, 31);

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String monthKey = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            double multiplier = FESTIVAL_MULTIPLIERS.getOrDefault(monthKey, 1.0);

            // Extra spike on festival peak days
            if (isFestivalPeak(date)) multiplier *= 1.5;

            for (Branch branch : branches) {
                int salesPerDay = (int) Math.max(1, Math.round((2 + rng.nextInt(4)) * multiplier));
                for (int s = 0; s < salesPerDay; s++) {
                    Sale sale = buildSale(branch.getId(), date, products, rng, multiplier);
                    sales.add(sale);
                }
            }
        }
        saleRepo.saveAll(sales);
    }

    private Sale buildSale(Long branchId, LocalDate date, List<Product> products,
                           Random rng, double multiplier) {
        Sale sale = new Sale();
        sale.setBranchId(branchId);
        sale.setSoldAt(date.atStartOfDay(ZoneOffset.UTC).plusSeconds(rng.nextInt(86400)).toInstant());

        int lineCount = 1 + rng.nextInt(5);
        List<Product> shuffled = new ArrayList<>(products);
        Collections.shuffle(shuffled, rng);

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < lineCount; i++) {
            Product p = shuffled.get(i % shuffled.size());
            int qty = (int) Math.max(1, Math.round((1 + rng.nextInt(10)) * multiplier * 0.5));
            BigDecimal price = BigDecimal.valueOf(20 + rng.nextInt(480));

            SaleLine line = new SaleLine();
            line.setSale(sale);
            line.setProductId(p.getId());
            line.setQuantity(qty);
            line.setUnitPrice(price);
            sale.getLines().add(line);
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        sale.setTotalAmount(total);
        sale.setVatAmount(BigDecimal.ZERO);
        return sale;
    }

    private boolean isFestivalPeak(LocalDate date) {
        int m = date.getMonthValue(), d = date.getDayOfMonth();
        return (m == 4 && d >= 8 && d <= 16)    // Eid ul-Fitr + Pohela Boishakh
            || (m == 6 && d >= 14 && d <= 20)   // Eid ul-Adha
            || (m == 10 && d >= 10 && d <= 15); // Durga Puja
    }

    // --- helpers ---
    private Branch branch(Long tid, String name, String address, String city, String phone) {
        Branch b = new Branch(); b.setTenantId(tid); b.setName(name);
        b.setAddress(address); b.setCity(city); b.setPhone(phone); return b;
    }

    private Category category(Long tid, String name) {
        Category c = new Category(); c.setTenantId(tid); c.setName(name); return c;
    }

    private Supplier supplier(Long tid, String name, String phone, int lead) {
        Supplier s = new Supplier(); s.setTenantId(tid); s.setName(name);
        s.setPhone(phone); s.setLeadTimeDays(lead); return s;
    }
}
