import AttendanceCore
import Foundation
import WidgetKit

@MainActor
final class LabelsViewModel: ObservableObject {
    struct Row: Identifiable {
        let subject: Subject
        var draft: String
        let defaultLabel: String
        var currentLabel: String

        var id: String { subject.code }
    }

    @Published private(set) var rows: [Row] = []
    @Published var statusText = "Labels stay on this device and are not sent to Maitri."
    @Published var statusIsError = false

    private let aliasStore: SubjectAliasStore

    init(aliasStore: SubjectAliasStore = .shared) {
        self.aliasStore = aliasStore
    }

    func load(from snapshot: AttendanceSnapshot?) {
        guard let snapshot else {
            rows = []
            statusText = "Refresh attendance first to load subjects."
            statusIsError = false
            return
        }
        rows = snapshot.subjects.map { subject in
            let defaultLabel = SubjectAliasStore.defaultLabel(for: subject)
            let current = aliasStore.displayLabel(for: subject)
            let draft = aliasStore.alias(for: subject.code) ?? ""
            return Row(subject: subject, draft: draft, defaultLabel: defaultLabel, currentLabel: current)
        }
        statusText = "Labels stay on this device and are not sent to Maitri."
        statusIsError = false
    }

    func updateDraft(rowID: String, draft: String) {
        guard let index = rows.firstIndex(where: { $0.id == rowID }) else { return }
        rows[index].draft = draft
    }

    func save(rowID: String) {
        guard let index = rows.firstIndex(where: { $0.id == rowID }) else { return }
        do {
            let subject = rows[index].subject
            try aliasStore.setAlias(code: subject.code, label: rows[index].draft)
            rows[index].currentLabel = aliasStore.displayLabel(for: subject)
            rows[index].draft = aliasStore.alias(for: subject.code) ?? ""
            statusText = "Saved label for \(subject.code)."
            statusIsError = false
            reloadWidget()
        } catch SubjectAliasStoreError.labelTooLong {
            statusText = "Labels can be at most \(SubjectAliasStore.maxAliasLength) characters."
            statusIsError = true
        } catch {
            statusText = "Could not save this label."
            statusIsError = true
        }
    }

    func reset(rowID: String) {
        guard let index = rows.firstIndex(where: { $0.id == rowID }) else { return }
        do {
            let subject = rows[index].subject
            _ = try aliasStore.clearAlias(code: subject.code)
            rows[index].draft = ""
            rows[index].currentLabel = aliasStore.displayLabel(for: subject)
            statusText = "Reset \(subject.code) to the default label."
            statusIsError = false
            reloadWidget()
        } catch {
            statusText = "Could not reset this label."
            statusIsError = true
        }
    }

    func resetAll() {
        do {
            try aliasStore.resetAll()
            for index in rows.indices {
                let subject = rows[index].subject
                rows[index].draft = ""
                rows[index].currentLabel = aliasStore.displayLabel(for: subject)
            }
            statusText = "All custom labels were cleared."
            statusIsError = false
            reloadWidget()
        } catch {
            statusText = "Could not clear custom labels."
            statusIsError = true
        }
    }

    private func reloadWidget() {
        WidgetCenter.shared.reloadTimelines(ofKind: AttendanceWidgetKind.identifier)
    }
}
