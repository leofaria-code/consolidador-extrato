package br.com.escalacaotech.extrato.consolidacao;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/**
 * Mapeia {@link YearMonth} (competência — glossário da linguagem ubíqua) para a
 * coluna texto {@code "AAAA-MM"}: legível em consulta direta ao banco e ordenável
 * lexicograficamente, o que basta para índices por competência.
 */
@Converter(autoApply = true)
public class ConversorYearMonth implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth competencia) {
        return competencia == null ? null : competencia.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String valor) {
        return valor == null ? null : YearMonth.parse(valor);
    }
}
