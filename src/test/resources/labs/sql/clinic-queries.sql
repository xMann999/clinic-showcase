-- Pięć zapytań laboratorium SQL. Każde poprzedza linia "-- name: <nazwa>".
-- Parametry nazwane (:doctorId) wiąże NamedParameterJdbcTemplate - nigdy nie sklejamy ich z tekstem SQL.

-- name: doctor_visits_on_day
-- Wizyty doktora w danym dniu: zakres półotwarty [początek dnia, następny dzień).
-- Indeks idx_visits_doctor_id_date_time: równość na doctor_id, potem zakres na date_time.
select v.id, v.date_time, v.duration_in_minutes, v.patient_id
from visits v
where v.doctor_id = :doctorId
  and v.date_time >= :dayStart
  and v.date_time < :nextDay
order by v.date_time;

-- name: patients_without_visits
-- LEFT JOIN zachowuje pacjentów bez dopasowania; IS NULL wybiera właśnie ich.
select p.first_name
from patients p
left join visits v on v.patient_id = p.id
where v.id is null
order by p.first_name;

-- name: busy_doctors
-- GROUP BY grupuje wiersze per doktor; HAVING filtruje GRUPY (WHERE filtruje wiersze przed grupowaniem).
select d.last_name, count(v.id) as visits
from doctors d
join visits v on v.doctor_id = d.id
group by d.id, d.last_name
having count(v.id) > 5;

-- name: free_slots
-- Wolny slot = brak pacjenta.
select v.id
from visits v
where v.patient_id is null
order by v.date_time, v.id;

-- name: patient_account_email
-- Dane konta są w users (normalizacja) - e-mail pacjenta odczytujemy przez JOIN, nie kopiujemy go.
select u.email
from patients p
join users u on u.id = p.user_id
where p.id_card_no = :idCardNo;

