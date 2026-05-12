import AttendanceCore
import Foundation
import XCTest

final class AttendanceCoreTests: XCTestCase {
    func testAbbreviationExamples() {
        XCTAssertEqual(makeSubject(name: "Artificial Intelligence in Electric Mobility").abbreviation, "AIEM")
        XCTAssertEqual(makeSubject(name: "Cryptography").abbreviation, "C")
        XCTAssertEqual(makeSubject(name: "Project-III").abbreviation, "P-III")
        XCTAssertEqual(makeSubject(name: "Theory of Computation").abbreviation, "TC")
    }

    func testTermSelectionUsesHighestSemesterId() throws {
        let terms = try parseTerms(from: loadFixture(named: "terms"))
        let current = terms.max(by: { $0.semesterId < $1.semesterId })
        XCTAssertEqual(current?.semesterId, 6)
        XCTAssertEqual(current?.name, "VI")
    }

    func testProjectSubjectsAreExcludedFromFixture() throws {
        let subjects = try parseSubjects(from: loadFixture(named: "subjects"))
        XCTAssertEqual(subjects.count, 1)
        XCTAssertEqual(subjects.first?.code, "CSE3727")
        XCTAssertEqual(subjects.first?.percentage ?? 0, 90, accuracy: 0.01)
    }

    func testSnapshotRoundTrip() throws {
        let snapshot = AttendanceSnapshot(
            termName: "VI",
            termSemesterId: 6,
            subjects: [makeSubject(name: "Cryptography", present: 8, absent: 2)],
            fetchedAtMillis: 1_700_000_000_000
        )
        let data = try JSONEncoder().encode(snapshot)
        let decoded = try JSONDecoder().decode(AttendanceSnapshot.self, from: data)
        XCTAssertEqual(decoded, snapshot)
        XCTAssertEqual(decoded.overallPercentage, 80, accuracy: 0.01)
    }

    func testRepositoryRateLimitLogic() {
        let cached = AttendanceSnapshot(termName: "VI", termSemesterId: 6, subjects: [], fetchedAtMillis: 1_000)
        let now = cached.fetchedAtMillis + 60_000
        XCTAssertTrue(now - cached.fetchedAtMillis < AttendanceRepository.defaultMinIntervalMillis)
    }

    func testAliasOverrideWinsOverGeneratedAbbreviation() throws {
        let subject = Subject(code: "CSE3727", name: "Cryptography", present: 8, absent: 2, afterCapping: 0)
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".json")
        let store = SubjectAliasStore(fileURL: fileURL)
        try store.setAlias(code: subject.code, label: "CRYPTO")
        XCTAssertEqual(store.displayLabel(for: subject), "CRYPTO")
        XCTAssertEqual(SubjectRowLayout.label(for: subject, aliasStore: store), "CRYPTO")
    }

    func testEmptyAliasFallsBackToDefaultLabel() throws {
        let subject = Subject(code: "CSE3727", name: "Cryptography", present: 8, absent: 2, afterCapping: 0)
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".json")
        let store = SubjectAliasStore(fileURL: fileURL)
        _ = try store.setAlias(code: subject.code, label: "   ")
        XCTAssertEqual(store.displayLabel(for: subject), "C")
    }

    func testLabelColumnWidthUsesResolvedLabels() throws {
        let subjects = [
            Subject(code: "A", name: "Alpha Beta", present: 1, absent: 0, afterCapping: 0),
            Subject(code: "B", name: "Gamma", present: 1, absent: 0, afterCapping: 0),
        ]
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".json")
        let store = SubjectAliasStore(fileURL: fileURL)
        try store.setAlias(code: "A", label: "LONGNAME")
        let width = SubjectRowLayout.labelColumnWidth(for: subjects, fontSize: 10, aliasStore: store)
        XCTAssertEqual(width, CGFloat("LONGNAME".count) * 10 * 0.62, accuracy: 0.01)
    }

    func testDetailsFormatsPercentAndCounts() {
        let subject = Subject(code: "A", name: "Alpha", present: 8, absent: 2, afterCapping: 0)
        XCTAssertEqual(SubjectRowLayout.details(for: subject), " 80.00% ( 8/10)")
    }

    func testAliasStoreRoundTrip() throws {
        let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".json")
        let store = SubjectAliasStore(fileURL: fileURL)
        try store.setAlias(code: "CSE3727", label: "AIEM")
        let reloaded = SubjectAliasStore(fileURL: fileURL)
        XCTAssertEqual(reloaded.alias(for: "CSE3727"), "AIEM")
        XCTAssertEqual(reloaded.allAliases(), ["CSE3727": "AIEM"])
    }

    private func makeSubject(
        name: String,
        present: Int = 0,
        absent: Int = 0
    ) -> Subject {
        Subject(code: "CODE", name: name, present: present, absent: absent, afterCapping: 0)
    }

    private func parseTerms(from data: Data) throws -> [Term] {
        let array = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] ?? []
        return array.compactMap { object in
            guard let semesterId = object["semesterId"] as? Int else { return nil }
            return Term(
                semesterId: semesterId,
                name: object["terms2Name"] as? String ?? "Sem \(semesterId)",
                startDate: object["teachingStartDate"] as? String ?? "?",
                endDate: object["teachingEndDate"] as? String ?? "?",
                subjectwiseStudentIds: object["subjectwiseStudentIds"] as? String ?? "",
                subejctwiseBatchIds: object["subejctwiseBatchIds"] as? String ?? "",
                batchsemesterCapacity: object["batchsemesterCapacity"] as? Int ?? 0
            )
        }
    }

    private func parseSubjects(from data: Data) throws -> [Subject] {
        let array = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] ?? []
        return array.compactMap { object -> Subject? in
            if let category = object["subjectCategory"] as? String, category == "PROJECT" {
                return nil
            }
            return Subject(
                code: object["subjectCode"] as? String ?? "?",
                name: object["subject"] as? String ?? "?",
                present: object["presentCount"] as? Int ?? 0,
                absent: object["absentCount"] as? Int ?? 0,
                afterCapping: object["afterCapping"] as? Double ?? 0
            )
        }
    }

    private func loadFixture(named name: String) throws -> Data {
        let bundle = Bundle.module
        let url = bundle.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")
            ?? bundle.url(forResource: name, withExtension: "json")
        guard let url else {
            throw NSError(domain: "AttendanceCoreTests", code: 1)
        }
        return try Data(contentsOf: url)
    }
}
