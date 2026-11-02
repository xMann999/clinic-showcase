-- Fixture laboratorium SQL na schemacie z migracji aplikacji (Liquibase).
-- Dane syntetyczne. Identyfikatory nadaje baza; test odnajduje je po unikalnych wartościach.
insert into users (email, password_hash, role) values ('alfa.doc@lab.test', 'x', 'USER');
insert into users (email, password_hash, role) values ('beta.doc@lab.test', 'x', 'USER');
insert into users (email, password_hash, role) values ('family@lab.test', 'x', 'USER');
insert into users (email, password_hash, role) values ('solo@lab.test', 'x', 'USER');

insert into doctors (first_name, last_name, specialization, user_id)
select 'Adam', 'Alfa', 'Cardiology', id from users where email = 'alfa.doc@lab.test';
insert into doctors (first_name, last_name, specialization, user_id)
select 'Beata', 'Beta', 'Neurology', id from users where email = 'beta.doc@lab.test';

insert into patients (first_name, last_name, id_card_no, birthday, user_id)
select 'Ewa', 'Rodzinna', 'SQL000001', DATE '1990-01-01', id from users where email = 'family@lab.test';
insert into patients (first_name, last_name, id_card_no, birthday, user_id)
select 'Jan', 'Rodzinny', 'SQL000002', DATE '2015-01-01', id from users where email = 'family@lab.test';
insert into patients (first_name, last_name, id_card_no, birthday, user_id)
select 'Ola', 'Samotna', 'SQL000003', DATE '1980-01-01', id from users where email = 'solo@lab.test';

-- Doktor Alfa: dokładnie 6 wizyt 2030-01-08, trzy zarezerwowane przez Ewę.
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 13:00:00', 30, d.id, null from doctors d where d.last_name = 'Alfa';
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 08:00:00', 30, d.id, p.id from doctors d, patients p where d.last_name = 'Alfa' and p.id_card_no = 'SQL000001';
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 09:00:00', 30, d.id, p.id from doctors d, patients p where d.last_name = 'Alfa' and p.id_card_no = 'SQL000001';
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 10:00:00', 30, d.id, p.id from doctors d, patients p where d.last_name = 'Alfa' and p.id_card_no = 'SQL000001';
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 11:00:00', 30, d.id, null from doctors d where d.last_name = 'Alfa';
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 12:00:00', 30, d.id, null from doctors d where d.last_name = 'Alfa';

-- Doktor Beta: dwie wizyty 2030-01-08 i jedna następnego dnia.
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 08:00:00', 60, d.id, p.id from doctors d, patients p where d.last_name = 'Beta' and p.id_card_no = 'SQL000001';
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-08 10:00:00', 30, d.id, null from doctors d where d.last_name = 'Beta';
insert into visits (date_time, duration_in_minutes, doctor_id, patient_id)
select TIMESTAMP '2030-01-09 00:00:00', 30, d.id, null from doctors d where d.last_name = 'Beta';

