package com.bpm.systems.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Document Indexing Service
 *
 * ETL Pipeline:
 * 1. Dokümanları oku (PDF, TXT, vb.)
 * 2. Parçalara böl (chunking)
 * 3. Embedding oluştur
 * 4. Vector Store'a kaydet
 */
@Service
public class DocumentIndexingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    public DocumentIndexingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        // 500 token chunks, 50 token overlap
        this.textSplitter = new TokenTextSplitter(500, 50, 5, 10000, true);

        log.info("✅ Document Indexing Service initialized");
    }

    /**
     * Tek bir text string'i index'le
     *
     * @param content  Text içeriği
     * @param metadata Metadata (source, type, vb.)
     */
    public void indexText(String content, Map<String, Object> metadata) {
        log.info("Indexing text content ({} chars)", content.length());

        try {
            // Document oluştur
            Document doc = new Document(content, metadata);

            // Parçalara böl
            List<Document> chunks = textSplitter.split(doc);

            log.debug("Split into {} chunks", chunks.size());

            // Her chunk'a timestamp ekle
            chunks.forEach(chunk -> chunk.getMetadata().put("indexed_at", LocalDateTime.now().toString()));

            // Vector Store'a kaydet (otomatik embedding yapılır)
            vectorStore.add(chunks);

            log.info("✅ Successfully indexed {} chunks", chunks.size());

        } catch (Exception e) {
            log.error("❌ Failed to index text", e);
            throw new RuntimeException("Text indexing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Dosyadan okuyup index'le
     *
     * @param filePath Dosya yolu
     */
    public void indexFile(String filePath) {
        log.info("Indexing file: {}", filePath);

        try {
            // Dosyayı oku
            String content = Files.readString(Path.of(filePath));

            // Metadata ekle
            Map<String, Object> metadata = Map.of(
                    "source", filePath,
                    "type", "file",
                    "filename", Path.of(filePath).getFileName().toString());

            indexText(content, metadata);

        } catch (Exception e) {
            log.error("❌ Failed to index file: {}", filePath, e);
            throw new RuntimeException("File indexing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Birden fazla text içeriğini toplu index'le
     *
     * @param documents Text dokümanları ve metadata'ları
     */
    public void indexDocuments(List<Map<String, Object>> documents) {
        log.info("Indexing {} documents", documents.size());

        List<Document> allChunks = new ArrayList<>();

        for (Map<String, Object> docMap : documents) {
            String content = (String) docMap.get("content");
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) docMap.getOrDefault("metadata", Map.of());

            // Document oluştur
            Document doc = new Document(content, metadata);

            // Parçalara böl
            List<Document> chunks = textSplitter.split(doc);

            // Timestamp ekle
            chunks.forEach(chunk -> chunk.getMetadata().put("indexed_at", LocalDateTime.now().toString()));

            allChunks.addAll(chunks);
        }

        log.debug("Total chunks to index: {}", allChunks.size());

        // Vector Store'a toplu kaydet
        vectorStore.add(allChunks);

        log.info("✅ Successfully indexed {} documents ({} chunks)",
                documents.size(), allChunks.size());
    }

    /**
     * Test amaçlı sample data yükle
     */
    public void loadSampleData() {
        log.info("Loading sample data...");

        List<Map<String, Object>> sampleDocs = List.of(
                Map.of(
                        "content",
                        """
                                Remote Work Policy

                                Our company supports flexible work arrangements to promote work-life balance and employee productivity.

                                Eligibility: All full-time employees who have completed their probation period (typically 3 months) are eligible for remote work.

                                Schedule: Employees may work remotely up to 3 days per week. The remaining 2 days must be spent at the office for team collaboration and meetings. Remote work days should be coordinated with your manager and team.

                                Requirements:
                                - Must have a dedicated home office space with reliable internet connection (minimum 50 Mbps)
                                - Must be available during core business hours (10 AM - 4 PM)
                                - Must attend all scheduled meetings via video conference with camera on
                                - Must respond to messages within 2 hours during work hours

                                Equipment: The company provides a laptop, monitor, keyboard, and mouse for remote work. Employees may request an ergonomic chair with manager approval.

                                Security: All company data must be accessed through VPN. Personal devices may not be used to access company systems without IT approval.
                                """,
                        "metadata", Map.of("type", "policy", "category", "remote-work", "department", "HR")),
                Map.of(
                        "content",
                        """
                                Vacation and Leave Policy

                                Annual Leave: All employees receive 20 days of paid vacation per year, accrued monthly. Unused vacation days may be carried over to the next year (maximum 5 days).

                                How to Request:
                                1. Submit leave request through the HR portal at least 2 weeks in advance
                                2. Manager approval is required for all leave requests
                                3. Requests for more than 5 consecutive days require VP approval

                                Sick Leave: Employees receive up to 10 paid sick days per year. A doctor's note is required for absences exceeding 3 consecutive days.

                                Parental Leave:
                                - Maternity leave: 16 weeks paid leave
                                - Paternity leave: 4 weeks paid leave
                                - Adoption leave: 12 weeks paid leave

                                Bereavement Leave: 5 days for immediate family, 3 days for extended family.

                                Public Holidays: The company observes 10 public holidays per year. The holiday calendar is published in December for the following year.
                                """,
                        "metadata", Map.of("type", "policy", "category", "leave", "department", "HR")),
                Map.of(
                        "content",
                        """
                                Health Insurance and Benefits

                                Medical Insurance: All employees and their dependents (spouse and children under 26) are covered by comprehensive health insurance from day one.

                                Coverage Includes:
                                - Hospital stays: 100% covered with no deductible
                                - Doctor visits: $20 copay per visit
                                - Prescription drugs: Generic $10, Brand $30, Specialty $50 copay
                                - Mental health services: 20 sessions per year fully covered
                                - Physical therapy: 30 sessions per year with $15 copay

                                Dental Coverage:
                                - Preventive care (cleanings, x-rays): 100% covered, 2 per year
                                - Basic procedures (fillings, extractions): 80% covered
                                - Major procedures (crowns, bridges): 50% covered
                                - Annual maximum: $2,000 per person

                                Vision Coverage:
                                - Annual eye exam: Fully covered
                                - Frames: $150 allowance every 2 years
                                - Lenses or contacts: $150 allowance per year

                                Life Insurance: 2x annual salary, up to $500,000 coverage at no cost. Additional coverage available for purchase.

                                401(k): Company matches 100% of contributions up to 6% of salary. Full vesting after 3 years.
                                """,
                        "metadata", Map.of("type", "policy", "category", "benefits", "department", "HR")),
                Map.of(
                        "content",
                        """
                                Performance Review Process

                                Review Cycle: Performance reviews are conducted twice annually in June and December.

                                Process:
                                1. Self-Assessment: Employees complete a self-assessment form reviewing their goals, achievements, and areas for improvement (due 2 weeks before review date)
                                2. Manager Review: Managers evaluate employee performance based on goals, competencies, and values alignment
                                3. Calibration: Department leadership meets to ensure fair and consistent ratings across teams
                                4. Feedback Session: One-on-one meeting between employee and manager to discuss results and set future goals

                                Rating Scale:
                                - Exceptional (5): Consistently exceeds all expectations and goals
                                - Exceeds (4): Frequently exceeds expectations in most areas
                                - Meets (3): Consistently meets all expectations
                                - Needs Improvement (2): Does not consistently meet expectations
                                - Unsatisfactory (1): Falls significantly below expectations

                                Impact on Compensation:
                                - Performance ratings directly influence annual merit increases (typically in March)
                                - Exceptional performers may receive additional equity grants or bonuses
                                - Employees rated "Needs Improvement" for two consecutive reviews enter a Performance Improvement Plan (PIP)

                                Goal Setting: Each employee should have 3-5 SMART goals set at the beginning of each review period.
                                """,
                        "metadata", Map.of("type", "policy", "category", "performance", "department", "HR")),
                Map.of(
                        "content",
                        """
                                Professional Development and Training

                                Learning Budget: Each employee has an annual professional development budget of $3,000 for courses, certifications, conferences, and books.

                                Eligible Expenses:
                                - Online courses (Coursera, Udemy, LinkedIn Learning, etc.)
                                - Professional certifications (AWS, PMP, Scrum, etc.)
                                - Industry conferences and workshops
                                - Technical books and subscriptions
                                - Professional association memberships

                                Approval Process:
                                1. Submit learning request through the Learning Portal
                                2. Include course description, cost, and relevance to your role
                                3. Manager approval required for expenses over $500
                                4. Expenses are reimbursed within 2 weeks of submission

                                Conference Attendance:
                                - Maximum 3 work days may be used for conference attendance
                                - Travel and accommodation are covered separately from learning budget
                                - Employees must share learnings with their team upon return

                                Internal Learning:
                                - Weekly Tech Talks every Friday (optional)
                                - Mentorship program available for all employees
                                - Internal certification programs for key technologies
                                - Tuition reimbursement for degree programs (up to $10,000/year with 2-year commitment)
                                """,
                        "metadata", Map.of("type", "policy", "category", "training", "department", "HR")),
                Map.of(
                        "content",
                        """
                                Expense Reimbursement Policy

                                Business Expenses: All reasonable business expenses are reimbursed within 30 days of submission.

                                Travel Expenses:
                                - Airfare: Economy class for domestic, business class for international flights over 6 hours
                                - Hotels: Up to $200/night domestic, $300/night international (major cities may have higher limits)
                                - Meals: Up to $75/day or actual expenses with receipts
                                - Ground transportation: Uber/Lyft preferred, rental cars need pre-approval

                                Client Entertainment:
                                - Meals with clients: Up to $150 per person
                                - Requires manager approval for expenses over $500
                                - Must include names and business purpose in receipt

                                Home Office Equipment:
                                - One-time $500 stipend for home office setup
                                - Monthly $100 internet/phone allowance for remote workers

                                How to Submit:
                                1. Upload receipts to Expensify within 30 days of expense
                                2. Categorize expense correctly and add business purpose
                                3. Submit report for manager approval
                                4. Reimbursement deposited with next payroll after approval

                                Corporate Credit Card: Available for employees with frequent travel, requires VP approval.
                                """,
                        "metadata", Map.of("type", "policy", "category", "expenses", "department", "Finance")));

        indexDocuments(sampleDocs);

        log.info("✅ Sample data loaded successfully");
    }
}
